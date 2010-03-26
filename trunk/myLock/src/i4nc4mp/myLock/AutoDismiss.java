package i4nc4mp.myLock;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
//This one is the dismiss activity method but runs no guard activity
//it just dismisses at wakeup and call end if the lockscreen is detected

public class AutoDismiss extends MediatorService implements SensorEventListener {
	public boolean persistent = false;
    //public boolean timeoutenabled = false;
	public boolean shakemode = false;
    
    public int patternsetting = 0;
    //we'll see if the user has pattern enabled when we startup
    //so we can disable it and then restore when we finish
    //FIXME store this in the prefs file instead of local var, so boot handler can pick it up and restore when necessary
    
   
    //public boolean idle = false;
    //when the idle alarm intent comes in we set this true to properly start closing down
    
//============Shake detection variables
    
    private static final int FORCE_THRESHOLD = 350;
    private static final int TIME_THRESHOLD = 100;
    private static final int SHAKE_TIMEOUT = 500;
    private static final int SHAKE_DURATION = 1000;
    private static final int SHAKE_COUNT = 3;
   
    //private SensorManager mSensorMgr;
    private float mLastX=-1.0f, mLastY=-1.0f, mLastZ=-1.0f;
    private long mLastTime;
    //private OnShakeListener mShakeListener;
    private Context mContext;
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
    	 SharedPreferences settings = getSharedPreferences("myLock", 0);
         shakemode = settings.getBoolean("shake", false);
    	
        Log.v("init shake","registering for shake");
        
        mContext = getApplicationContext();
        // Obtain a reference to system-wide sensor event manager.
        mSensorEventManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);

        // Get the default sensor for accel
        mSensor = mSensorEventManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Register for events.
        if (shakemode) mSensorEventManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        //I don't have a use for shake while not in sleep (yet)
        //workaround appears contingent on keeping it registered while awake also
        //i will just unregister on first start
        
        //IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        //registerReceiver(mReceiver, filter);
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
            
        SharedPreferences settings = getSharedPreferences("myLock", 0);
        SharedPreferences.Editor editor = settings.edit();
            
            if (patternsetting == 1) {
            	            	
                android.provider.Settings.System.putInt(getContentResolver(), 
                    android.provider.Settings.System.LOCK_PATTERN_ENABLED, 1);
                    
                
        		editor.putBoolean("securepaused", false);
        		
        		// Don't forget to commit your edits!!!
        		//editor.commit();
            }

                
                //unregisterReceiver(idleExit);
            	unregisterReceiver(lockStopped);
                               
                
                editor.putBoolean("serviceactive", false);
                editor.commit();
                
                //ManageWakeLock.releasePartial();
                
             // Unregister from SensorManager.
                if (shakemode) mSensorEventManager.unregisterListener(this);
                //unregisterReceiver(mReceiver);
                
}
    /*
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
 public void onReceive(Context context, Intent intent) {
            // Check action just to be on the safe side.
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                // Unregisters the listener and registers it again.
                mSensorEventManager.unregisterListener(AutoDismiss.this);
                mSensorEventManager.registerListener(AutoDismiss.this, mSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
            }
 }
    };*/

    @Override
    public void onRestartCommand() {
            
            SharedPreferences settings = getSharedPreferences("myLock", 0);
            boolean fgpref = settings.getBoolean("FG", true);
            boolean shakepref = settings.getBoolean("shake", false);
                                 
/*========Settings change re-start commands that come from settings activity*/
    
            if (persistent != fgpref) {//user changed pref
                    if (persistent) {
                                    stopForeground(true);//kills the ongoing notif
                                    persistent = false;
                    }
                    else doFGstart();//so FG mode is started again
            }
            else if (shakemode != shakepref) {
            		shakemode = shakepref;
            	}
            else {
/*========Safety start that ensures the settings activity toggle button can work, first press to start, 2nd press to stop*/
                  Log.v("toggle request","user first press of toggle after a startup at boot");
                    }               
    }
    
    @Override
    public void onFirstStart() {
            SharedPreferences settings = getSharedPreferences("myLock", 0);
            SharedPreferences.Editor editor = settings.edit();
            
            persistent = settings.getBoolean("FG", true);
            //timeoutenabled = settings.getBoolean("timeout", false);
                        
            if (persistent) doFGstart();
            if (shakemode) mSensorEventManager.unregisterListener(this);
                        
            //we have to toggle pattern lock off to use a custom lockscreen
            try {
                    patternsetting = android.provider.Settings.System.getInt(getContentResolver(), android.provider.Settings.System.LOCK_PATTERN_ENABLED);
            } catch (SettingNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
            }
            
            if (patternsetting == 1) {      
    android.provider.Settings.System.putInt(getContentResolver(), 
                    android.provider.Settings.System.LOCK_PATTERN_ENABLED, 0);
    
    			
    			editor.putBoolean("securepaused", true);
    			//will be flagged off on successful exit w/ restore of pattern requirement
    			//otherwise, it is caught by the boot handler... if myLock gets force closed/uninstalled
    			//there's no clean resolution to this pause.

    			// Don't forget to commit your edits!!!
    			//editor.commit();
            }
            
            
            //ManageWakeLock.acquirePartial(getApplicationContext());
            //if not always holding partial we would only acquire at Lock activity exit callback
            //we found we always need it to ensure key events will not occasionally drop on the floor from idle state wakeup
            
            /*
            IntentFilter idleFinish = new IntentFilter ("i4nc4mp.myLock.lifecycle.IDLE_TIMEOUT");
            registerReceiver(idleExit, idleFinish);
            
             * 
             */
            
            IntentFilter lockStop = new IntentFilter ("i4nc4mp.myLock.lifecycle.LOCKSCREEN_EXITED");
            registerReceiver(lockStopped, lockStop);
            
         
            
            
            editor.putBoolean("serviceactive", true);
            editor.commit();
    }
    
    /*
    BroadcastReceiver idleExit = new BroadcastReceiver() {
            @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals("i4nc4mp.myLock.lifecycle.IDLE_TIMEOUT")) return;
                            
            idle = true;
            
            PowerManager myPM = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
            myPM.userActivity(SystemClock.uptimeMillis(), true);
            
            Log.v("mediator idle reaction","preparing to restore KG.");
                        
            //the idle flag will cause proper handling on receipt of the exit callback from lockscreen
            //we basically unlock as if user requested, but then force KG back on in the callback reaction
    }};*/
    
    BroadcastReceiver lockStopped = new BroadcastReceiver() {
        @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals("i4nc4mp.myLock.lifecycle.LOCKSCREEN_EXITED")) return;
        
        //couldn't get any other method to avoid the KG from shutting screen back off
        //when dismiss activity sent itself to back
        //it would ignore all user activity pokes and log "ignoring user activity while turning off screen"
        
        ManageWakeLock.releaseFull();
        if (shakemode) mSensorEventManager.unregisterListener(AutoDismiss.this);
        return;
        }};
    
    @Override
    public void onScreenWakeup() {
    	ManageKeyguard.initialize(getApplicationContext());
        
        boolean KG = ManageKeyguard.inKeyguardRestrictedInputMode();    
    	if (KG) StartDismiss(getApplicationContext());                
        
    	return;
    }
    
    @Override
    public void onScreenSleep() {
    	//mSensorEventManager.unregisterListener(AutoDismiss.this);
        if (shakemode) mSensorEventManager.registerListener(AutoDismiss.this, mSensor,
            SensorManager.SENSOR_DELAY_NORMAL);
        //standard workaround runs the listener at all times.
        //i will only register at off and release it once we are awake
    }
    
    public void StartDismiss(Context context) {
            
    	//PowerManager myPM = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        //myPM.userActivity(SystemClock.uptimeMillis(), true);
    	ManageWakeLock.acquireFull(getApplicationContext());
    	
    Class w = AutoDismissActivity.class; 
                  
    Intent dismiss = new Intent(context, w);
    dismiss.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK//required for a service to launch activity
                    | Intent.FLAG_ACTIVITY_NO_USER_ACTION//Just helps avoid conflicting with other important notifications
                    | Intent.FLAG_ACTIVITY_NO_HISTORY//Ensures the activity WILL be finished after the one time use
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    
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
            //this will tell us if the phone ever restored the keyguard
            //phone occasionally brings it back to life but suppresses it
            
            //2.1 isScreenOn will allow us the logic:
            
            //restart lock if it is asleep and relocked
            //dismiss lock if it is awake and relocked
            //do nothing if it is awake and not re-locked
            //wake up if it is asleep and not re-locked (not an expected case)
            
            //right now we will always dismiss
            /*
            if (callWake) {
                    Log.v("wakeup call end","restarting lock activity.");
                    callWake = false;
                    PendingLock = true;
                    StartLock(mCon);
                    //when we restart here, the guard activity is getting screen on event
                    //and calling its own dismiss as if it was a user initiated wakeup
                    //TODO but this logic will be needed for guarded custom lockscreen version
            }
            else {
            	//KG may or may not be about to come back and screen may or may not be awake
            	//these factors depend on what the user did during call
            	//all we will do is dismiss any keyguard that exists, which will cause wake if it is asleep
            	//if (IsAwake()) {}
                    Log.v("call end","checking if we need to exit KG");
                    shouldLock = true;
                    if (KG) StartDismiss(mCon);
            }*/
            
            //shouldLock = true;
            if (KG) StartDismiss(mCon);
            
    }
    
//============================
    
    public void onShake() {
   	
   	Log.v("onShake","doing wakeup");
   	StartDismiss(getApplicationContext());
   	//this causes wake as it happens just due to our code that's there already to prevent invalid sleep
       
    }
    
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //not used right now
    }
    
    //Used to decide if it is a shake
    public void onSensorChanged(SensorEvent event) {
            if(event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
            
            Log.v("sensor","sensor change is verifying");
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
    
    void doFGstart() {
            //putting ongoing notif together for start foreground
            
            //String ns = Context.NOTIFICATION_SERVICE;
            //NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
            //No need to get the mgr, since we aren't manually sending this for FG mode.
            
            int icon = R.drawable.icon;
            CharSequence tickerText = "myLock is starting up";
            
            long when = System.currentTimeMillis();

            Notification notification = new Notification(icon, tickerText, when);
            
            Context context = getApplicationContext();
            CharSequence contentTitle = "myLock - click to open settings";
            CharSequence contentText = "lockscreen is disabled";

            Intent notificationIntent = new Intent(this, SettingsActivity.class);
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

            notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);
            
            final int SVC_ID = 1;
            
            //don't need to pass notif because startForeground will do it
            //mNotificationManager.notify(SVC_ID, notification);
            persistent = true;
            
            startForeground(SVC_ID, notification);
    }
}