package com.example.gps_app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;

import java.util.TimeZone;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.views.MapView;



public class MainActivity extends AppCompatActivity {

    int PERMISSION_ID = 44;

    long current_delay = 500;
    long prev_time;

    SimpleDateFormat sdf;

    FileInputStream fis;
    XmlPullParserFactory factory;
    XmlPullParser gpx_parser;

    MapView map = null;
    IMapController mapController;
    Marker prev_marker = null;



    Boolean play;


    Button play_button,pause_button;
    VideoView mVideoView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, Recording.class);
        startActivity(intent);

        //Setting time format
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));


//        Load/initialize the osmdroid configuration, this can be done
//        setting this before the layout is inflated is a good idea
//        it 'should' ensure that the map has a writable location for the map cache, even without permissions
//        if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
//        see also StorageUtils
//        note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's tile servers will get you banned based on this string
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        org.osmdroid.config.IConfigurationProvider osmConf = org.osmdroid.config.Configuration.getInstance();
        File basePath = new File(getFilesDir().getAbsolutePath()+"/osmdroid");
        osmConf.setOsmdroidBasePath(basePath);
        File tileCache = new File(getFilesDir().getAbsolutePath()+"/osmdroid/tiles");
        osmConf.setOsmdroidTileCache(tileCache);


        setContentView(R.layout.activity_main);


        // Buttons and video view
        pause_button = (Button) findViewById(R.id.pause_button);
        play_button = (Button) findViewById(R.id.play_button);
        mVideoView = (VideoView) findViewById(R.id.videoView);


        //Configuring map display
        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);

        mapController = map.getController();
        mapController.setZoom(18.0);


        //Location of Video File
        Uri uri = Uri.parse(getFilesDir().getAbsolutePath()+"/Rec1.mp4");
        mVideoView.setVideoURI(uri);
        mVideoView.requestFocus();


        play_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                start_playback();
            }
        });


        pause_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stop_gps_playback();
                //Video may be longer than gpx file
                stop_video_playback();
            }
        });

        mVideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                stop_video_playback();
            }
        });
    }

    @Override
    protected void onPause(){
        super.onPause();
        stop_gps_playback();
        mVideoView.stopPlayback();
    }


    // Open and start reading from GPX file
    private void open_gpx_read(){
        String filename = "Rec1.gpx";

        //Opening file and setting factory
        try {
            fis = new FileInputStream(new File(getFilesDir(), filename));
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
        }
        catch (IOException | XmlPullParserException e){
            e.printStackTrace();
        }


        //Linking input xml and parser
        try {
            gpx_parser = factory.newPullParser();
            gpx_parser.setInput(fis, null);
        }
        catch (XmlPullParserException e){
            e.printStackTrace();
        }


        //Parsing till trkpt
        try {
            int eventType = gpx_parser.getEventType();

            while (true) {

                if (eventType == XmlPullParser.START_TAG){
                    if (gpx_parser.getName().equals("trkseg")){
                        gpx_parser.next();
                        gpx_parser.next();
                        break;
                    }
                    else
                        gpx_parser.getName();
                }
                else if (eventType == XmlPullParser.END_TAG) {
                    gpx_parser.getName();
                }
                else if (eventType == XmlPullParser.TEXT) {
                    gpx_parser.getText();
                }

                eventType = gpx_parser.next();
            }

        }
        catch (IOException | XmlPullParserException e){
            e.printStackTrace();
        }

    }


    //Update map with current co-ordinates
    private void update_map(){
        GeoPoint nextGeoPoint = get_next_location();
        if (nextGeoPoint==null){
                stop_gps_playback();
                return;
        }
        mapController.setCenter(nextGeoPoint);

        if (prev_marker!=null){
            map.getOverlays().remove(prev_marker);
        }
        Marker marker = new Marker(map);
        marker.setPosition(nextGeoPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(marker);
        marker.setIcon(MainActivity.this.getResources().getDrawable(R.drawable.center));
        prev_marker = marker;
        map.invalidate();
    }

    
    // Function to begin playing of video and gps data
    private void start_playback(){

           if(!mVideoView.isPlaying() && gpx_parser==null){
               open_gpx_read();
               play = true;

               start_video_playback();
               //Update to initial position
               update_map();
               update_map();

               final Handler handler = new Handler();
               handler.postDelayed(new Runnable() {
                   @Override
                   public void run() {
                       if(play){
                           update_map();
                           handler.postDelayed(this,current_delay);
                       }
                       else{
                           handler.removeCallbacks(this);
                       }
                   }
               }, current_delay);
           }
           else{
                 Toast.makeText(getApplicationContext(),"Playback already started",Toast.LENGTH_SHORT).show();
           }
    }


    private void stop_gps_playback(){
        play = false;
        gpx_parser = null;
        factory = null;
    }


    private void start_video_playback(){
        //Start video playing
        mVideoView.seekTo(0);
        mVideoView.start();
    }


    private void stop_video_playback(){
        if (mVideoView.isPlaying()){
            mVideoView.pause();
        }
    }


    //Get next location from currently open gpx file
    private GeoPoint get_next_location(){


        if(gpx_parser.getName().equals("trkseg")){
            return null;
        }


        Double Latitude =  Double.parseDouble(gpx_parser.getAttributeValue(null,"lat"));
        Double Longitude = Double.parseDouble(gpx_parser.getAttributeValue(null,"lon"));
        GeoPoint nextGeoPoint = new GeoPoint(Latitude,Longitude);

        try {
            gpx_parser.next();
            gpx_parser.getText();//blank
            gpx_parser.next();
            gpx_parser.getName();//ele
            gpx_parser.next();
            gpx_parser.getText();//ele value
            gpx_parser.next();
            gpx_parser.getName();//ele close
            gpx_parser.next();
            gpx_parser.getText();//Blank
            gpx_parser.next();
            gpx_parser.getName();//Time
            gpx_parser.next();
            
            //Updating time to wait before fetching next location from gpx file
            long new_time = sdf.parse(gpx_parser.getText(),new ParsePosition(0)).getTime();
            Log.d("time",Long.toString(new_time));
            Log.d("time",Long.toString(prev_time));
            current_delay =  new_time - prev_time; //Time value
            prev_time = new_time;
            Log.d("time",Long.toString(current_delay));
            
            
            gpx_parser.next();
            gpx_parser.getName();//Time close
            gpx_parser.next();
            gpx_parser.getText();//Blank

            gpx_parser.next();
            gpx_parser.getName();// trkpt close
            gpx_parser.next();
            gpx_parser.getText();//Blank

            gpx_parser.next();
            gpx_parser.getName();//new trkpt

        }
        catch (IOException | XmlPullParserException e){
            e.printStackTrace();
        }
        
        return nextGeoPoint;
    }





    private boolean checkPermissions(){
        if( ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    private void requestPermissions(){
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSION_ID
        );
    }


    private boolean isLocationEnabled(){
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }


}
