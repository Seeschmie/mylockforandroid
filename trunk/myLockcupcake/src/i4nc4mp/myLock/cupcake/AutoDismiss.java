package i4nc4mp.myLock.cupcake;

import i4nc4mp.myLock.cupcake.ManageKeyguard.LaunchOnKeyguardExit;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

//Ported from 2.1 by replacing the dismiss activity with the keyguard manager API
//It's not great because the timing is obtuse and it is not exactly the definition
//of a smooth end user experience.

//TODO it might be possible to port the actual activity by calling disable in the onCreate
//then calling secure exit in onStart


//we mediate wakeup & call end, to fire Disable KG & Secure Exit if the lockscreen is detected

public class AutoDismiss extends MediatorService implements SensorEventListener {
	public boolean persistent = false;
    public boolean timeoutenabled = false;
    
	public boolean shakemode = false;
	public boolean slideGuarded = false;

    
    public boolean security = false;
    
    public boolean slideWakeup = false;
  //we will set this when we detect slideopen, only used with instant unlock
    
    
    public boolean dismissed = false;
    //will just toggle true after dismiss callback - used to help ensure airtight lifecycle
    
    public boolean callmissed = false;
    
    Handler serviceHandler;
    Task myTask = new Task();
    
//============Shake detection variables
    
    private static final int FORCE_THRESHOLD = 350;
    private static final int TIME_THRESHOLD = 100;
    private static final int SHAKE_TIMEOUT = 500;
    private static final int SHAKE_DURATION = 1000;
    private static final int SHAKE_COUNT = 3;
   
    private float mLastX=-1.0f, mLastY=-1.0f, mLastZ=-1.0f;
    private long mLastTime;

    private int mShakeCount = 0;
    private long mLastShake;
    private long mLastForce;
    
    //====
    SensorManager mSensorEventManager;
    
    Sensor mSensor;
    
    
    @Override
    public void onCreate() {
    	super.onCreate();
    	
    	//================register for shake listening, first time
    	//Log.v("init shake","connecting to sensor service and accel sensor");
        
        // Obtain a reference to system-wide sensor event manager.
        //mSensorEventManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);

        // Get the default sensor for accel
        //mSensor = mSensorEventManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
            
        SharedPreferences settings = getSharedPreferences("myLock", 0);
        SharedPreferences.Editor editor = settings.edit();
        
        
        	
            if (security) {
            //restore security lock 	
                android.provider.Settings.System.putInt(getContentResolver(), 
                    android.provider.Settings.System.LOCK_PATTERN_ENABLED, 1);
            }

            
            	unregisterReceiver(lockStopped);
                            	
            	settings.unregisterOnSharedPreferenceChangeListener(prefslisten);
                
                editor.putBoolean("serviceactive", false);
                editor.commit();
                
                serviceHandler.removeCallbacks(myTask);
                serviceHandler = null;
                
                //ManageWakeLock.releasePartial();
                
             // Unregister from SensorManager.
                //if (shakemode) mSensorEventManager.unregisterListener(this);
                
}
    
    @Override
    public void onFirstStart() {
    	
    	//first acquire the prefs that need to be initialized
            SharedPreferences settings = getSharedPreferences("myLock", 0);
            SharedPreferences.Editor editor = settings.edit();
            
            
            shakemode = settings.getBoolean("shake", false);
            slideGuarded = settings.getBoolean("slideGuard", false);
                        
            security = settings.getBoolean("security", false);
            //We need to check for security mode if we have a pattern when attempting to start
            //if the security mode isn't on, we need to notify user and abort start
            
            //if (shakemode) mSensorEventManager.unregisterListener(this);
            //turn off shake listener that we got in onCreate as we only start at sleep
            
            
            timeoutenabled = (settings.getInt("idletime", 0) != 0);
            
            //register a listener to update this if pref is changed to 0
            settings.registerOnSharedPreferenceChangeListener(prefslisten);
 
            
           //toggle out of security
            if (security) {
    android.provider.Settings.System.putInt(getContentResolver(), 
                    android.provider.Settings.System.LOCK_PATTERN_ENABLED, 0);
            }
            	
            serviceHandler = new Handler();
            
            IntentFilter lockStop = new IntentFilter ("i4nc4mp.myLock.lifecycle.LOCKSCREEN_EXITED");
            registerReceiver(lockStopped, lockStop);
            
            
            editor.putBoolean("serviceactive", true);
            editor.commit();
    }
    
    @Override
    public void onRestartCommand() {
    	timeoutenabled = (getSharedPreferences("myLock", 0).getInt("idletime", 0) != 0);
    }
    
    SharedPreferences.OnSharedPreferenceChangeListener prefslisten = new OnSharedPreferenceChangeListener () {
    	@Override
    	public void onSharedPreferenceChanged (SharedPreferences sharedPreference, String key) {
    		Log.v("pref change","the changed key is " + key);
    		
      		if ("shake".equals(key)) shakemode = sharedPreference.getBoolean(key, false);
    		if ("slideGuard".equals(key)) slideGuarded = sharedPreference.getBoolean(key, false);
    		}
    	};
    
    BroadcastReceiver lockStopped = new BroadcastReceiver() {
        @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals("i4nc4mp.myLock.lifecycle.LOCKSCREEN_EXITED")) return;
        
       
       
        
        
        //if (shakemode) mSensorEventManager.unregisterListener(AutoDismiss.this);
        return;
        }};
        
        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            //if (!slideGuarded) return;
            
            
            if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
                    //this means that a config change happened and the keyboard is open.     
            	if(!dismissed) {
            		Log.v("slider wake event","setting state flag, screen state is " + isScreenOn());
            		slideWakeup = true;    
            	}
            	else Log.v("slider event","Ignoring since already dismissed");
            }
            else if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
            	Log.v("slide closed","mediator got the config change from background");
            }          
        }
        
        
       
    
        public boolean isScreenOn() {
        	//Allows us to tap into the 2.1 screen check if available
        	
        	if(Integer.parseInt(Build.VERSION.SDK) < 7) { 
        		
        		return IsAwake();
        		//this comes from mediator superclass, checking the bool set by screen on/off broadcasts
        		//it is unreliable in phone calls when prox sensor is changing screen state
        		
        	}
        	else return IsAwake();
        	/*else {
        		PowerManager myPM = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        		return myPM.isScreenOn();
        		//unlike following the broadcasts this one is accurate. 
        		//most people have 2.1 now so it should be a non-issue
        	}*/
        }
        
        public void doExit(Context m) {
        	//ManageKeyguard.disableKeyguard(m);
        	StartDismiss(m);
        	//serviceHandler.postDelayed(myTask, 50L);
        }
        
        class Task implements Runnable {
            public void run() {
                    ManageKeyguard.exitKeyguardSecurely(new LaunchOnKeyguardExit() {
                public void LaunchOnKeyguardExitSuccess() {
                   Log.v("doExit", "This is the exit callback");
                   ManageKeyguard.reenableKeyguard();
                    }});
            }}
        
    @Override
    public void onScreenWakeup() {    	
    	//if (timeoutenabled) IdleTimer.cancel(getApplicationContext());
        //since now awake, cancel idle alarm. should be every wake so we can cancel if call causes wake
    	
    	//now check for call state flags
    	if (receivingcall || placingcall || callmissed) {
    		Log.v("auto dismiss service","aborting screen wake handling due to call state");
    		if (callmissed) callmissed = false;
    		return;
    	}    	
    	//this event happens at the ignore/miss due to the lockscreen appearing
    	//it is actually a bug in the lockscreen that sends the screen on when it was already on
    	
    	if (slideGuarded && slideWakeup) return;
    	//no dismiss when slide guard active
    	
    	//now let's see if the KG is even up
    	ManageKeyguard.initialize(getApplicationContext());
    	boolean KG = ManageKeyguard.inKeyguardRestrictedInputMode();
    	
    	if (KG) doExit(getApplicationContext());                
        
    	return;
    }
    
    @Override
    public void onScreenSleep() {
    	//mSensorEventManager.unregisterListener(AutoDismiss.this);
        if (shakemode) mSensorEventManager.registerListener(AutoDismiss.this, mSensor,
            SensorManager.SENSOR_DELAY_NORMAL);
        //standard workaround runs the listener at all times.
        //i will only register at off and release it once we are awake
        
        
        dismissed = false;//flag will allow us to know we are coming into a slide wakeup
        callmissed = false;//just in case we didn't get the bad screen on after call is missed
        
        if (slideWakeup) {
        	Log.v("back to sleep","turning off slideWakeup");
            slideWakeup = false;
        }
        
        /*if (timeoutenabled) {
        	Log.v("idle lock","starting timer");
        	IdleTimer.start(getApplicationContext());
        }*/
        //we need to get user present here to effectively know if user unlocked from a slide wake
        //right now we are allowing the timer to restart with the activity of slide wake but not unlock
        //when we would really want it to continue without restarting in that situation
       
    }
    
    
    public void StartDismiss(Context context) {
            	
    Class w = DismissActivity.class; 
                  
    Intent dismiss = new Intent(context, w);
    dismiss.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK//required for a service to launch activity
                    | Intent.FLAG_ACTIVITY_NO_USER_ACTION//Just helps avoid conflicting with other important notifications
                    | Intent.FLAG_ACTIVITY_NO_HISTORY);//Ensures the activity WILL be finished after the one time use
                    //| Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    
    context.startActivity(dismiss);
}
    
//============Phone call case handling
    
    //we have many cases where the phone reloads the lockscreen even while screen is awake at call end
    //my testing shows it actually comes back after any timeout sleep plus 5 sec grace period
    //then phone is doing a KM disable command at re-wake. and restoring at call end
    //that restore is what we intercept in these events as well as certain treatment based on lock activity lifecycle
    
    @Override
    public void onCallEnd() {
            //all timeout sleep causes KG to visibly restore after the 5 sec grace period
            //the phone appears to be doing a KM disable to pause it should user wake up again, and then re-enables at call end
            
            //if call ends while asleep and not in the KG-restored mode (watching for prox wake)
            //then KG is still restored, and we can't catch it due to timing
            
            //right now we can't reliably check the screen state
    		//instead we will restart the guard if call came in waking up device
    		//otherwise we will just do nothing besides dismiss any restored kg
            
            Context mCon = getApplicationContext();
            
            Log.v("call end","checking if we need to exit KG");
            
            ManageKeyguard.initialize(mCon);
            
            boolean KG = ManageKeyguard.inKeyguardRestrictedInputMode();
            if (KG) doExit(mCon);
            
    }
    
    @Override
    public void onCallMiss() {
    	callmissed = true;
    	//flag so we can suppress handling of the screen on we seem to get at phone state change
    }
    
//============================
    
    public void onShake() {
   	
   	Log.v("onShake","doing wakeup");
   	
       
    }
    
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //not used right now
    }
    
    //Used to decide if it is a shake
    public void onSensorChanged(SensorEvent event) {
            if(event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
            
            //Log.v("sensor","sensor change is verifying");
            //uncomment this to be certain the sensor is registered
            //it will spam it continuously while properly registered
        long now = System.currentTimeMillis();
     
        if ((now - mLastForce) > SHAKE_TIMEOUT) {
          mShakeCount = 0;
        }
     
        if ((now - mLastTime) > TIME_THRESHOLD) {
          long diff = now - mLastTime;
          float speed = Math.abs(event.values[SensorManager.DATA_X] + event.values[SensorManager.DATA_Y] + event.values[SensorManager.DATA_Z] - mLastX - mLastY - mLastZ) / diff * 10000;
          if (speed > FORCE_THRESHOLD) {
            if ((++mShakeCount >= SHAKE_COUNT) && (now - mLastShake > SHAKE_DURATION)) {
              mLastShake = now;
              mShakeCount = 0;
              
            //call the reaction you want to have happen
              onShake();
            }
            mLastForce = now;
          }
          mLastTime = now;
          mLastX = event.values[SensorManager.DATA_X];
          mLastY = event.values[SensorManager.DATA_Y];
          mLastZ = event.values[SensorManager.DATA_Z];
        }
            
    }
//====End shake handling block
}