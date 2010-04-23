package i4nc4mp.myLock;


import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

//perhaps toggling duty can be made a part of the ManageMediator as a static method
public class Toggler extends Service {
	
	private boolean target;
	private boolean active;
	
	private boolean guard = false;
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
Log.v("Toggler","Starting");
		
		getPrefs();
		
		active = ManageMediator.bind(getApplicationContext());
		
		target = intent.getBooleanExtra("i4nc4mp.myLock.TargetState", !active);
		
		Log.v("toggling","target is " + target + " and current state is " + active);
		
		//start if we've been told to start and did not already exist				
		if (target && !active) {
			startService();
			updateEnablePref(true, getApplicationContext());
    		Toast.makeText(Toggler.this, "myLock is now enabled", Toast.LENGTH_SHORT).show();
    		
		}//stop if we've been told to stop and did already exist
		else if (active && !target) {
				
				stopService();
				Toast.makeText(Toggler.this, "myLock is now disabled", Toast.LENGTH_SHORT).show();
				
				updateEnablePref(false, getApplicationContext());
		}//log the request - locale condition may send a desired state that already exists
		else Log.v("toggler","unhandled outcome - target was not a change");
		
		//added to prevent android "restarting" this after it dies/is purged causing unexpected toggle
		stopSelf();//close so it won't be sitting idle in the running services window
		return START_NOT_STICKY;//ensure it won't be restarted by the OS, we only want explicit starts
	}
	
public void getPrefs() {
	SharedPreferences settings = getSharedPreferences("myLock", 0);
	guard = settings.getBoolean("wallpaper", false);
}

private void updateEnablePref(boolean on, Context mCon) {
	SharedPreferences set = getSharedPreferences("myLock", 0);
	SharedPreferences.Editor editor = set.edit();
    editor.putBoolean("enabled", on);

    // Don't forget to commit your edits!!!
    editor.commit();
    
    
    AppWidgetManager mgr = AppWidgetManager.getInstance(mCon);
    ComponentName comp = new ComponentName(mCon.getPackageName(), ToggleWidget.class.getName());
    //int[] widgets = mgr.getAppWidgetIds (comp);
    RemoteViews views = new RemoteViews(mCon.getPackageName(), R.layout.togglelayout);
    
    Intent i = new Intent();
	i.setClassName("i4nc4mp.myLock", "i4nc4mp.myLock.Toggler");
	PendingIntent myPI = PendingIntent.getService(mCon, 0, i, 0);
	
	//attach an on-click listener to the button element
	views.setOnClickPendingIntent(R.id.toggleButton, myPI);
    
	int img;
    //on = ManageMediator.bind(context);
    if (on) img = R.drawable.widg_on_icon;
    else img = R.drawable.widg_off_icon;
    
    //tell the button element what state image to show
    views.setImageViewResource(R.id.toggleButton, img);
    
    mgr.updateAppWidget(comp, views);
    
    /*
    ComponentName comp = new ComponentName(mCon.getPackageName(), ToggleWidget.class.getName());
    Intent w = new Intent();
    
    w.setComponent(comp);
    w.setAction("android.appwidget.action.APPWIDGET_UPDATE");
    w.putExtra("i4nc4mp.myLock.toggle", true);//so widget knows we manually told it to update the status
    mCon.sendBroadcast(w);
    */
    
    
}
	
private void startService(){
		Intent i = new Intent();
		if (guard) i.setClassName("i4nc4mp.myLock", "i4nc4mp.myLock.BasicGuardService");
		else i.setClassName("i4nc4mp.myLock", "i4nc4mp.myLock.AutoDismiss");
		startService(i);
		ManageMediator.bind(getApplicationContext());//we always hold the binding while officially active
		Log.d( getClass().getSimpleName(), "startService()" );
}

private void stopService() {
		ManageMediator.release(getApplicationContext());
		Intent i = new Intent();
		if (guard) i.setClassName("i4nc4mp.myLock", "i4nc4mp.myLock.BasicGuardService");
		else i.setClassName("i4nc4mp.myLock", "i4nc4mp.myLock.AutoDismiss");
		stopService(i);
		Log.d( getClass().getSimpleName(), "stopService()" );
}

}