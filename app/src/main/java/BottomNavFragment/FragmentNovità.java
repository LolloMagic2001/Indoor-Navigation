package BottomNavFragment;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.views.MapView;

import android.widget.CheckedTextView;
import android.widget.ImageButton;
import android.widget.TextView;

import dataAndRelation.Controller;

import com.example.osmdroidex2.BuildConfig;
import com.example.osmdroidex2.R;

import org.osmdroid.util.GeoPoint;

public class FragmentNovità extends Fragment {
    MapView map;
    private TextView geoLocation;
    private TextView position;
    private TextView locazione;
    private TextView bluethoot;
    private TextView versione;
    LocationManager locationManager;
    private ImageButton bike;
    private ImageButton walk;
    private ImageButton car;

    //Fare per ogni problema un listener per modificare il valore nelle sharedPreferences
    private CheckedTextView Ansia;
    private CheckedTextView carrozzina;
    private CheckedTextView Claustrofobia;

    Controller controller;

    double soglia =0.5;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_novita, container, false);
        bike =(ImageButton) view.findViewById(R.id.bike);
        walk =(ImageButton)  view.findViewById(R.id.walking);
        carrozzina= (CheckedTextView)   view.findViewById(R.id.carrozina);
        car =(ImageButton)  view.findViewById(R.id.car);
        controller = Controller.getInstance(null, null,null);
        locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        geoLocation = (TextView) view.findViewById(R.id.geoLocation);
        position = (TextView) view.findViewById(R.id.position);
        locazione = (TextView) view.findViewById(R.id.locazione);
        bluethoot = (TextView) view.findViewById(R.id.bluethoot);
        versione = (TextView) view.findViewById(R.id.versione);
        position.setText(R.string.Default);
        locazione.setText(R.string.Default);

        if(isGPSEnabled()){
            geoLocation.setText("True");
        }else{
            geoLocation.setText("False");
        }
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            geoLocation.setText(R.string.Default);
        }
        if(ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 15, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    Log.d("fatto", "refresh");
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    String latitudeS = String.format("%.2f", latitude);
                    String longitudeS = String.format("%.2f", longitude);
                    position.setText(latitudeS+", "+ longitudeS);

                    double minDistanza = 1;
                    GeoPoint geoVicino = null;
                    for(GeoPoint g : controller.getEdificio()){
                        Log.d("fatto", "geo0 " + g);
                        double distanza = calcolaDistanza(g.getLatitude(),g.getLongitude(),location.getLatitude(),location.getLongitude());
                        Log.d("fatto", "distanza " + distanza);
                        if(distanza < minDistanza){
                            minDistanza = distanza;
                            geoVicino = g;
                        }
                    }
                    Log.d("fatto", "geo " + geoVicino);
                    if(minDistanza<=soglia){
                        Log.d("fatto", "nome " + (controller.getName(geoVicino)));
                        locazione.setText(controller.getName(geoVicino));
                    }else{
                        locazione.setText(R.string.Default);
                    }
                }

                //Cosa fare se tolgo il gps
                @Override
                public void onProviderDisabled(String provider) {
                    geoLocation.setText(R.string.False);
                }

                //Cosa fare se abilito il gps
                @Override
                public void onProviderEnabled(String provider) {
                    geoLocation.setText(R.string.True);
                }

                @Override
                public void onStatusChanged(String provider, int status,
                                            Bundle extras) {
                }
            });
        }

        //Bluethoot attivo
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        boolean isBluetoothEnabled = bluetoothAdapter.isEnabled();

        bluethoot.setText(String.valueOf(isBluetoothEnabled));;

        BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    int bluetoothState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                    if (bluetoothState == BluetoothAdapter.STATE_ON) {
                        bluethoot.setText(R.string.True);
                    } else if (bluetoothState == BluetoothAdapter.STATE_OFF) {
                        bluethoot.setText(R.string.False);
                    }
                }
            }
        };

        // filtro specifica che l'applicazione desidera ricevere gli intenti che notificano i cambiamenti di stato del Bluetooth.
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        //getContext().registerReceiver(bluetoothReceiver, filter), il tuo BroadcastReceiver sarà
        // in grado di ricevere gli intenti associati ai cambiamenti dello stato del Bluetooth
        getContext().registerReceiver(bluetoothReceiver, filter);

        String versionName = BuildConfig.VERSION_NAME;
        if(versionName==null){
            versione.setText(R.string.Default);
        }else{
            versione.setText(versionName);
        }

        /*SharedPreferences sharedPreferences = getContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("edificio", edificioScoperto);
        editor.apply();*/

        SharedPreferences sharedPreferences = getContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        bike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("proviamolo", " bike");
                editor.putBoolean("bike",true);
                editor.putBoolean("car",false);
                editor.apply();
                /*button1Selected = true;
                button2Selected = false;
                button3Selected = false;
                updateButtonState();*/
            }
        });
        car.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("proviamolo", " car");
                editor.putBoolean("car",true);
                editor.putBoolean("bike",false);
                editor.apply();
                /*button1Selected = false;
                button2Selected = true;
                button3Selected = false;
                updateButtonState();*/
            }
        });
        walk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("proviamolo", " walk");
                editor.putBoolean("car",false);
                editor.putBoolean("bike",false);
                editor.apply();
                /*button1Selected = false;
                button2Selected = false;
                button3Selected = true;
                updateButtonState();*/
            }
        });

        carrozzina.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("proviamolo", " carrozina 1");
                if(sharedPreferences.getBoolean("carrozzina",false)){
                    editor.putBoolean("carrozzina", false);
                    Log.d("proviamolo", " carrozina 2");
                }else{
                    editor.putBoolean("carrozzina", true);
                    Log.d("proviamolo", " carrozina 3");
                }
                editor.apply();
            }
        });

        return view;
    }

    private boolean isGPSEnabled() {
        boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        return isGPSEnabled;
    }

    //Formula di Haversin, calcolo della distanza in km per due punti Geo
    public static double calcolaDistanza(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // Raggio medio della Terra in chilometri

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = R * c;

        return distance;
    }

    /*private void updateButtonState() {
        if (button1Selected) {
            bike.setSelected(true);
        } else {
            bike.setSelected(false);
        }

        if (button2Selected) {
            car.setSelected(true);
        } else {
            car.setSelected(false);
        }

        if (button3Selected) {
            walk.setSelected(true);
        } else {
            walk.setSelected(false);
        }
    }*/

}
