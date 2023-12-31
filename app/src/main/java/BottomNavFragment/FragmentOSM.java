package BottomNavFragment;


import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import org.osmdroid.api.IMapController;
import org.osmdroid.bonuspack.routing.OSRMRoadManager;
import org.osmdroid.bonuspack.routing.RoadManager;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.GroundOverlay;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import android.content.Context;
import android.graphics.Bitmap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;


import Adapter.CustomAdapter;
import Adapter.NetworkViewModel;
import dataAndRelation.Aula;
import dataAndRelation.Edificio;
import dataAndRelation.Controller;
import dataAndRelation.ViewModel;

import com.example.osmdroidex2.GpsManager;
import com.example.osmdroidex2.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Marker;

public class FragmentOSM extends Fragment {

    //per la listView
    private GridView gridView;
    Marker scelta;
    private CustomAdapter adapter;
    private List<String> items;
    // Dichiarazione del timer
    CountDownTimer timer;
    boolean listenerGps=true;
    ArrayList<GeoPoint> posizioneEdifici;
    MapView map;
    String edificioScoperto = null;
    GroundOverlay overlay;
    ArrayList<GeoPoint> waypoints2;
    Polyline roadOverlay;
    RoadManager roadManager;
    ImageButton visitaGuidata;
    ImageButton focusPoint;
    ImageButton location;
    // Dichiarazione delle variabili per la partenza e la destinazione selezionate dallo spinner
    String partenzaSelezionata = null;
    String destinazioneSelezionata = null;
    //Controller e manager per poter lavorare con i punti geospaziali.
    private IMapController mapController;
    //Viene usato per capire dove l'utente stia zommando e quindi capire se mostrare i bottoni dei layer
    BoundingBox box;
    //Ho creato una classe intermezza tra la mia applicazione e i dati. Così l'unica classe che si dovrà
    //andare a modificare è in questo caso il PreDatabase.
    Controller controller;
    //Tiene conto della destinazione selezionata, se è un aula prende quel valore se no ritorna null
    Marker aulaSelezionata;
    boolean posizioneAttuale = false;

    ImageButton logoReset;
    GpsManager gpsManager;

    //Per collegarsi a Room
    private ViewModel mViewModel;
    private NetworkViewModel networkViewModel;
    boolean primavolta = false;
    AutoCompleteTextView spinnerDestinazione;
    AutoCompleteTextView spinnerPartenza;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = new ViewModelProvider(this).get(ViewModel.class);
        //devo metterlo dove chiedo l'edificio, listener ogni volta che il dato cambia
        mViewModel.getAllEdificios().observe(getActivity(), new Observer<List<Edificio>>() {
            @Override
            public void onChanged(List<Edificio> edificios) {
                mViewModel.getAllAule().observe(getActivity(), new Observer<List<Aula>>() {
                    @Override
                    public void onChanged(List<Aula> aulas) {
                        //Non ricreo l'oggetto se è Singlenton, ma inverità non mi serve che cambia, a sto punto non serve manco il
                        //LiveData
                        controller = Controller.newChange(getContext(),edificios,aulas);
                        posizioneEdifici =controller.getEdificio();
                        ///Initialiaze serve per andare a creare il routing etc... prima che parti l'applicaizone
                        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
                        String destinazione = sharedPreferences.getString("destinazione",null);
                        String partenza = sharedPreferences.getString("partenza", null);
                        /////////////////////////////////////////////////////
                        try {
                            initialiaze(partenza, destinazione);
                        } catch (ExecutionException e) {
                            throw new RuntimeException(e);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        ///////////
                        map.invalidate();
                    }
                });
            }
        });
        networkViewModel = new ViewModelProvider(this).get(NetworkViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_osm,container,false);

        // Registra un BroadcastReceiver per rilevare i cambiamenti di connessione
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        getActivity().registerReceiver(networkChangeReceiver, intentFilter);


        spinnerPartenza = view.findViewById(R.id.spinner_partenza);
        spinnerDestinazione = view.findViewById(R.id.spinner_destinazione);

        spinnerDestinazione.setFilters(new InputFilter[]{new InputFilter.LengthFilter(18)});
        spinnerPartenza.setFilters(new InputFilter[]{new InputFilter.LengthFilter(18)});
        //per resettare le scelte
        /*logoReset=view.findViewById(R.id.imageButton);*/

        //i button chiamati in questo modo orribile sono per i 2 layer aggiunti
        focusPoint=(ImageButton) view.findViewById(R.id.focus);
        location = (ImageButton) view.findViewById(R.id.location);

        visitaGuidata = (ImageButton) view.findViewById(R.id.appGhero);
        visitaGuidata.setVisibility(View.GONE);

        //per la listView
        gridView = view.findViewById(R.id.grid_view);
        gridView.setVisibility(View.GONE);

        items = new ArrayList<>();
        adapter = new CustomAdapter(getContext(), items);
        gridView.setAdapter(adapter);

        //popolo le mie scelte, successivamente si andranno a prendere dal database
        List<String> opzioniPartenza = Arrays.asList(getString(R.string.blank),getString(R.string.u14),
                getString(R.string.u6),getString(R.string.posizioneAttuale),getString(R.string.u7),
                getString(R.string.u14AulaFirstFloor),getString(R.string.u14AulaSecondFloor),getString(R.string.u6AulaFirstFloor));
        List<String> opzioniDestinazione = Arrays.asList(getString(R.string.u6),getString(R.string.u14),
                getString(R.string.u7), getString(R.string.u14AulaFirstFloor),getString(R.string.u14AulaSecondFloor), getString(R.string.u6AulaFirstFloor));

        // Crea un adapter per le opzioni di selezione della partenza
        ArrayAdapter<String> adapterPartenza = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, opzioniPartenza);
        adapterPartenza.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPartenza.setAdapter(adapterPartenza);

        // Crea un adapter per le opzioni di selezione della destinazione
        ArrayAdapter<String> adapterDestinazione = new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, opzioniDestinazione);
        adapterDestinazione.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDestinazione.setAdapter(adapterDestinazione);

        //Serve per inizializzare il testo anche se non lo rende effettivo
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String destinazione = sharedPreferences.getString("destinazione",null);
        String partenza = sharedPreferences.getString("partenza", null);
        spinnerPartenza.setText(partenza);
        spinnerDestinazione.setText(destinazione);

        //visto che non funge bene il click e la scelta la implemento in maniera rozza
        partenzaSelezionata = partenza;
        destinazioneSelezionata = destinazione;

        /////////////////////

        Context ctx = getContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        //Per scegliere il Routing dagli sharedPreferences
        chooseTransport();

        /*getPermissionExternalStorage();*/

        //Vado a creare la mappa
        map = (MapView)  view.findViewById(R.id.map);
        mapController = map.getController();
        map.setBuiltInZoomControls(true);
        map.setHorizontalMapRepetitionEnabled(false);
        map.setVerticalMapRepetitionEnabled(false);
        //Then we add default zoom buttons, and ability to zoom with 2 fingers (multi-touch)
        map.setBuiltInZoomControls(true);
        map.setMultiTouchControls(true);

        //Per vedere se l'utente ha abilitato la rete, e quindi scegliere la mappa da mostrare
        //RESETTARE IN AUTOMATICO LE 2 EDITTEXT
        networkViewModel.getNetworkStatus().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean aBoolean) {
                        map.setScrollableAreaLimitDouble(null);
                        //We can move the map on a default view point. For this, we need access to the map controller:
                        mapController.setZoom(18);
                        mapController.setCenter(new GeoPoint(45.5149, 9.2106));

                        //serve per rimuovere lo zoom automatico
                        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
                        // Qui puoi ridefinire i tile della mappa di OSMDroid in base allo stato di connessione
                        if (aBoolean) {
                            map.setUseDataConnection(true);
                            // Connessione Wi-Fi/4G attiva
                            map.setTileSource(TileSourceFactory.MAPNIK);
                            map.setMaxZoomLevel(19.5);
                            map.setMinZoomLevel(15.0);
                            Toast.makeText(getContext(), "Connessione Wi-Fi attiva", Toast.LENGTH_SHORT).show();
                        } else {
                            //la mappa sarebbe da aggiornare con firebase, sarebbe da vedere se è possibile modificare la cartella :"assets/tiles"
                            map.setUseDataConnection(false);
                            map.setTileSource(new XYTileSource("tiles", 0, 18, 256, ".png", new String[] {"tiles/"}));
                            BoundingBox b = new BoundingBox(45.5295, 9.2451, 45.5098,9.1953 );
                            map.setScrollableAreaLimitDouble(b);

                            //per regolare il max/min zoom
                            map.setMaxZoomLevel(19.5);
                            map.setMinZoomLevel(17.0);
                            // Nessuna connessione attiva
                            Toast.makeText(getContext(), "Nessuna connessione attiva", Toast.LENGTH_SHORT).show();
                        }
                        // Aggiorna la mappa
                        map.invalidate();
                    }
                });

        //Marker per mostrare solo la destinazione
        scelta= new Marker(map);

        //**** chiama il metodo enableMyLocationOverlay
        gpsManager=new GpsManager(map);
        gpsManager.enableMyLocationOverlay();

        //per avere solo una porzione di mappa dell'uni
       /*map.setScrollableAreaLimitLatitude(45.5329, 45.5075, 0);
        map.setScrollableAreaLimitLongitude(9.1841, 9.2241, 0);*/

        //per avere solo una porzione di mappa di casa mia
       /* map.setScrollableAreaLimitLatitude(45.61506, 45.61118, 0);
        map.setScrollableAreaLimitLongitude(9.15603, 9.16372, 0);*/

        //Vado a creare due marker che mi vanno a mostrare il percorso, e il waypoint che poi servirà
        //per andare a creare il percorso tra questi 2 punti
        /*GeoPoint startPoint = new GeoPoint(45.52379, 9.21958);
        GeoPoint endPoint = new GeoPoint(45.52378, 9.2198);
        GeoPoint middlePoint = new GeoPoint(45.52376, 9.21968);
        Marker startMarker = new Marker(map);
        startMarker.setId("start");
        startMarker.setIcon(getResources().getDrawable(R.drawable.baseline_heart_broken_24));
        startMarker.setPosition(startPoint);
        startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        addRemoveMarker(true, startMarker);

        Marker endMarker = new Marker(map);
        endMarker.setPosition(endPoint);
        endMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        addRemoveMarker(true, endMarker);

        List<GeoPoint> points = new ArrayList<>();
        points.add(startPoint);
        points.add(middlePoint);
        points.add(endPoint);
        Polyline line = new Polyline();
        line.setPoints(points);
        line.setColor(Color.RED);
        line.setWidth(5f);

        // Add the Polyline to the map view
        addRemoveLayerLine(true, line);*/

        /*boxUni = map.getBoundingBox();*/

        //serve per fare una polilinea animata
        /*waypoints = new ArrayList<GeoPoint>();
        waypoints.add(startPoint);
        waypoints.add(middlePoint);
        waypoints.add(endPoint);
        Animation animation = new Animation(map, waypoints, ctx);
        animation.addOverlays();*/

        //Mostra la bussola
        /*CompassOverlay compassOverlay = new CompassOverlay(ctx, map);
        compassOverlay.enableCompass();
        map.getOverlays().add(compassOverlay);*/

        //invalidare la mappa per aggiornarla, al cambio di qualcosa della mappa è consigliato
        //aggiornarla.
        map.invalidate();

        //******//GPS//
        gpsManager.gpsStart();

        /*logoReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                spinnerPartenza.setText("");
                spinnerDestinazione.setText("");
                partenzaSelezionata=null;
                destinazioneSelezionata=null;
                posizioneAttuale=false;
                addRemoveMarker(false,aulaSelezionata);
                addRemoveLayerLine(false,roadOverlay);
                map.getOverlayManager().remove(overlay);

            }
        });*/

        // Imposta il listener sul menu a tendina della partenza
        spinnerPartenza.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Quando viene selezionata un'opzione dalla lista della partenza, si salva il valore in una variabile
                partenzaSelezionata = (String) parent.getItemAtPosition(position);
                addRemoveMarker(false, aulaSelezionata);
                addRemoveLayerLine(false,roadOverlay);
                // Se entrambe le scelte sono state selezionate allora vado ad eseguire il codice
                if (partenzaSelezionata != null && destinazioneSelezionata != null && map != null) {
                    calculateExternalRouting();

                }//Se l'utente non ha scelto una partenza allora mostro solo la destinazione
                if(destinazioneSelezionata != null && map != null){
                    pointDestination(destinazioneSelezionata);
                }
            }

        });



        // Imposta il listener sul menu a tendina della destinazione, stesse considerazioni del listener
        // della partenza.
        spinnerDestinazione.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int posizione, long id) {
                // Quando viene selezionata un'opzione dalla lista della destinazione, si salva il valore in una variabile
                destinazioneSelezionata = (String) adapterView.getItemAtPosition(posizione);
                addRemoveMarker(false, aulaSelezionata);
                addRemoveLayerLine(false,roadOverlay);
                // Eseguire l'operazione desiderata con il valore selezionato
                if (partenzaSelezionata!=null && destinazioneSelezionata != null && map != null) {
                    calculateExternalRouting();
                }//ho messo qua un if che si ripete col primo perchè in questo modo va a farmi queste operazioni sia se partenza è null o non null
                if(destinazioneSelezionata != null && map != null){
                    pointDestination(destinazioneSelezionata);
                }
            }
        });


        //Per selezionare il piano corretto
        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //per cambiare piano devo esserci sopra con la visuale e devo zoommare
                getFloorEdificio(position, false, destinazioneSelezionata);
            }
        });

        //Serve per andare al punto di destinazione
        focusPoint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(destinazioneSelezionata!=null){
                    gpsManager.disableFollowLocation();
                    mapController = map.getController();
                    mapController.animateTo(controller.getGeoPoint(destinazioneSelezionata));
                }
            }
        });

        //serve per capire quando si zoomma dall'evento dell'ingrandimento con le due dita
        // Imposta il timer con una durata di 1 secondo (1000 millisecondi)
        timer = new CountDownTimer(150, 250) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Questo metodo viene chiamato ad ogni tick del timer (non necessario in questo caso)
            }

            @Override
            public void onFinish() {
                box = map.getBoundingBox();
                boolean pointContained = false;
                edificioScoperto=null;
                if (map.getZoomLevelDouble() >= 19) {
                    for (GeoPoint geo : posizioneEdifici) {
                        if (box.contains(geo)) {
                            edificioScoperto = controller.getName(geo);
                            Log.d("destination",edificioScoperto);
                            pointContained = true;
                        }
                    }
                    if (pointContained == true) {
                        Log.d("destination","c"+destinazioneSelezionata);
                        Log.d("destination","d"+controller.getAppartenenza(destinazioneSelezionata));
                        Log.d("destination","e"+edificioScoperto);
                        if(destinazioneSelezionata==null){
                            gridView.setVisibility(View.VISIBLE);
                            updateItems(controller.getNumberOfFloor(edificioScoperto));
                            visitaGuidata.setVisibility(View.VISIBLE);
                        }else if((destinazioneSelezionata != null && edificioScoperto.equals(controller.getAppartenenza(destinazioneSelezionata))) ||
                                (partenzaSelezionata != null && destinazioneSelezionata != null && edificioScoperto.equals(controller.getAppartenenza(partenzaSelezionata)))){
                            Log.d("destination","entrato");
                            gridView.setVisibility(View.VISIBLE);
                            updateItems(controller.getNumberOfFloor(edificioScoperto));
                            visitaGuidata.setVisibility(View.VISIBLE);
                        }else{
                            Log.d("destination","balzato");
                            gridView.setVisibility(View.GONE);
                            visitaGuidata.setVisibility(View.GONE);
                        }
                    } else {
                        gridView.setVisibility(View.GONE);
                        visitaGuidata.setVisibility(View.GONE);
                        if(controller.getFloor(destinazioneSelezionata) == null) {
                            map.getOverlays().remove(overlay);
                            overlay = null;
                        }
                    }
                } else {
                    edificioScoperto = null;
                    gridView.setVisibility(View.GONE);
                    visitaGuidata.setVisibility(View.GONE);
                    if(controller.getFloor(destinazioneSelezionata) == null) {
                        map.getOverlays().remove(overlay);
                        overlay = null;
                    }
                }
            }
        };

        map.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                timer.cancel(); // Annulla il timer precedente se presente
                timer.start();  // Avvia il nuovo timer
                return false;
            }
        });

        //Andare nella parte della Navigazione Indoor, dovrà usare edificioTrovato come variabile,
        //per capire a che edificio si riferisce
        visitaGuidata.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Serve per il click(ma non resetta tutto)
                BottomNavigationView bottomNavigationView = getActivity().findViewById(R.id.bottom_navigation);
                bottomNavigationView.setSelectedItemId(R.id.fragmentIndoor);

                //Vado a prendere il navController, dell'activity , uso il navController per gestire la navigazione dei fragment
                //senza il navController il fragment aveva dei comportamenti strani andando a portare dei conflitti
                //cambiando solo alcune view(mentre per altre le lasciava inalterate EditText), quindi per avere una gestione
                //migliore l'ho utilizzato e vado a cancellare
                //ogni volta il fragment indicato, poichè se no va a mantenerli tutti in memoria(+Destroy).
                NavController navController = Navigation.findNavController(getActivity(), R.id.nav_host_fragment);
                navController.popBackStack(R.id.fragmentIndoor, true);
                navController.navigate(R.id.fragmentIndoor);
            }
        });

        //Questo bottone serve a chiedere i permessi del gps se non li si avesse, con tutti i vari
        //controlli. Nel caso si avesse i permessi allora funziona come il bottone del gps normale
        location.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (gpsManager.gpsEnabled() && isNetworkConnected()) {
                    Log.d("getLocation","prova il get");
                    getLocation();
                } else {
                    showAlertMessageLocationDisabled();
                }
            }
        });

        //Prima del locationManager bisogna vedere questi permessi, devo vedere se riesco a toglierli
        //però per ora funziona, quindi lascio così. Devo chiedere i permessi ogni qualvolta abiliti il gps
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            getLocation();
        }
        //Forse questo if devo toglierlo, devo metterlo e ogni volta che abilito o faccio qualche operazione col gps, chiedo i permessi
        if(ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            getLocation();
        }

        return view;
    }

    private void getLocation(){
        if(ActivityCompat.checkSelfPermission(getContext(),Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            if(listenerGps==true){
                Log.d("gpsenabled", "funge10");
                locationListener();
                listenerGps=false;
            }
            if(partenzaSelezionata!="Posizione Attuale"){
                gpsManager.enableMyLocation();
            }else{
                mapController.animateTo(gpsManager.getMyLocationNewOverlay());
            }

        } else {
            Log.d("gpsenabled", "funge12");
            requestPermission();
        }
    }

    private void getPosizioneAttuale(){
        if(ActivityCompat.checkSelfPermission(getContext(),Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            waypoints2.add(gpsManager.getMyLocationNewOverlay());
            mapController.animateTo(gpsManager.getMyLocationNewOverlay());
            Log.d("gpsenabled", "funge13");
        } else {
            Log.d("gpsenabled", "funge14");
            requestPermission();
        }
    }

    private void showAlertMessageLocationDisabled(){
        AlertDialog.Builder builder =new AlertDialog.Builder(getContext());
        builder.setMessage("Device location is turned off, Turn on The device location, Do you want to turn on location?");
        builder.setCancelable(false);
        Log.d("gpsenabled", "funge150");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                Log.d("gpsenabled", "funge151");
            }
        });
        builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Log.d("gpsenabled", "funge152");
                dialogInterface.cancel();
            }
        });
        Log.d("gpsenabled", "funge153");
        AlertDialog dialog=builder.create();
        dialog.show();
    }

    private void showAlertMessageConnectionDisabled() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage("Device location or Wi-Fi is turned off. Turn on the device location and Wi-Fi. Do you want to turn them on?");
        builder.setCancelable(false);
        Log.d("gpsenabled", "funge150");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                Log.d("gpsenabled", "funge151");

            }
        });
        builder.setNegativeButton("NO", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.cancel();
            }
        });
        Log.d("gpsenabled", "funge153");
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void requestPermission(){
        Log.d("gpsenabled", "funge15");
        requestPermissions(
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},10);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d("gpsenabled", "funge16");
        if (requestCode == 10) {
            Log.d("gpsenabled", "funge17");
            if (grantResults.length ==1  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("gpsenabled", "funge18");
                getLocation();
            } else {
                Log.d("gpsenabled", "funge19");
                // Autorizzazione negata, gestire di conseguenza
            }
        }

        /*if (requestCode == REQUEST_WRITE_STORAGE) {
            Log.d("entratoPermission", "ciao4");
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permesso di scrittura ottenuto
                // Puoi procedere con la modifica della cartella "emulated"
                Log.d("entratoPermission", "ciao5");
            } else {
                Log.d("entratoPermission", "ciao6");
                // Permesso di scrittura negato
                // Gestisci il caso in cui l'utente non concede i permessi necessari
            }
        }*/
    }
    /////////////////////////////////////////////////

    private void addRemoveMarker(boolean add, Marker marker) {
        if(add == true) {
            map.getOverlayManager().add(marker);
        } else {
            map.getOverlayManager().remove(marker);
        }
        map.invalidate();
    }

    public void addRemoveLayerLine(boolean add, Polyline line){
        if(add == true) {
            map.getOverlayManager().add(line);
        }else{
            map.getOverlayManager().remove(line);
        }
        map.invalidate();
    }

    /**
     * int i sta ad indicare il piano;
     * call sta ad indicare chi lo chiama, poichè la destinazione e l'edificio scoperto(dallo zoom) sono
     * due comportamenti completamente diversi;
     * */
    //Con questo metodo vado a mostrare la bitMap relative al piano indicato
    public void getFloorEdificio(int i, boolean call, String destinazioneSelezionata){
        if(call){
            Log.d("provaGetFloor", "call");
            //In questo caso non posso affidarmi alla camera del punto in cui sta guardando l'utente
            //ma devo guardare la destinazione e capire a che edificio sta indicando così da scegliere
            //la mappa corretta da mostrare(bitmap)
            getFloorDestination(i, destinazioneSelezionata);
        }else{
            //Sono sopra l'edificio e quindi posso prendere il punto direttamente a dove sto guardando
            //con il metodo onTouch e onTick
            getFloorCam(i, destinazioneSelezionata);
        }

    }

    public void getFloorCam(int i, String destinazioneSelezionata){
        String edificio = edificioScoperto;
        ArrayList<GeoPoint> pointGroundOverley = controller.getPlanimetria(edificioScoperto);
        int position = i ;
        Bitmap bitmap = controller.getMap(edificio,position);
        Log.d("provaGetFloor", edificio+" B "+bitmap+ " O " + overlay+" position "+ position);

        if (overlay != null) {
            map.getOverlayManager().remove(overlay);
            Log.d("provaGetFloor", "yes");
            map.invalidate();
        }

        if(bitmap!=null) {
            Log.d("provaGetFloor", "no");
            overlay = new GroundOverlay();
            overlay.setTransparency(0.0f);
            overlay.setImage(bitmap);

            //Vado a scegliere i punti dove andare a mettere la mappa
            overlay.setPosition(pointGroundOverley.get(0), pointGroundOverley.get(1), pointGroundOverley.get(2), pointGroundOverley.get(3));
            //Scelgo l'overlay su dove metterla
            map.getOverlayManager().add(overlay);

            //Tiene traccia della destinazione, se è un aula allora va a stampare il marker dell'aula quando si va sul piano giusto
            if (destinazioneSelezionata != null){
                if (controller.getFloor(destinazioneSelezionata) != null && controller.getFloor(destinazioneSelezionata) == position) {
                    // Aggiunta del marker alla mappa
                    addRemoveMarker(false, aulaSelezionata);
                    addRemoveMarker(true, aulaSelezionata);
                } else if(controller.getFloor(destinazioneSelezionata) != null && controller.getFloor(destinazioneSelezionata) != position){
                    addRemoveMarker(false, aulaSelezionata);
                }
            }
            map.invalidate();
        }
    }

    public void getFloorDestination(int i, String destinazioneSelezionata){
        String appartenenza = controller.getAppartenenza(destinazioneSelezionata);
        String edificio = appartenenza;
        ArrayList<GeoPoint> pointGroundOverley = controller.getPlanimetria(edificio);
        int position = i ;
        Bitmap bitmap = controller.getMap(edificio,position);
        Log.d("provaGetFloor", edificio+" B "+bitmap+ "O " + overlay+" position "+ position);

        if (overlay != null) {
            map.getOverlayManager().remove(overlay);
            map.invalidate();
        }

        if(bitmap!=null) {
            overlay = new GroundOverlay();
            overlay.setTransparency(0.0f);
            overlay.setImage(bitmap);

            //Vado a scegliere i punti dove andare a mettere la mappa
            overlay.setPosition(pointGroundOverley.get(0), pointGroundOverley.get(1), pointGroundOverley.get(2), pointGroundOverley.get(3));
            //Scelgo l'overlay su dove metterla
            map.getOverlayManager().add(overlay);

            //Mostro l'aula
            addRemoveMarker(false, aulaSelezionata);
            addRemoveMarker(true, aulaSelezionata);

            map.invalidate();
        }
    }


    public void locationListener(){
        if(ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            Log.d("gpsenabled", "funge0");
        }
        Log.d("gpsenabled", "funge1");
        gpsManager.getLocationManager().requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 5, new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                //PARTE DEL RICALCOLO DEL PERCORSO SE SI SBAGLIA STRADA
                if(posizioneAttuale == true ) {

                    //serve per capire se sono fuori dall'uni
                    GeoPoint currentLocation = new GeoPoint(location.getLatitude(), location.getLongitude());
                    /* BoundingBox boxUni2 = map.getBoundingBox();*/

                    //Fatta per non calcolare il percorso fuori dalla mappa dell'uni, poichè pensavo che
                    //se sono fuori dall'area dell'uni, non vado manco a calcolare il percorso. Così
                    //da avere solo un pezzo di mappa. La parte commentata è proprio questo comportamento
                    //che ho rimosso

                    /*if (boxUni2.contains(currentLocation)) {*/
                    if(!map.getOverlayManager().contains(gpsManager.getMyLocationNewOverlay())){
                        gpsManager.followMe();
                    }
                    //mylocover abilita la posizione e il focus su di essa e quindi senza enableFollow non potrei andarmene
                    if (waypoints2 != null && roadOverlay != null) {

                        // Verifica se la distanza supera la soglia massima
                        ArrayList<GeoPoint> strada = (ArrayList<GeoPoint>) roadOverlay.getActualPoints();
                        double minDistance = 100;
                        for (GeoPoint roadPoint : strada) {
                            Location roadLocation = new Location("");
                            roadLocation.setLatitude(roadPoint.getLatitude());
                            roadLocation.setLongitude(roadPoint.getLongitude());
                            double distance = location.distanceTo(roadLocation);
                            if (distance < minDistance) {
                                minDistance = distance;
                            }
                        }

                        if (minDistance >= 50) {
                            waypoints2 = new ArrayList<GeoPoint>();
                            waypoints2.add(currentLocation);
                            /*FragmentOSM.ExecuteTaskInBackGround ex = new FragmentOSM.ExecuteTaskInBackGround();
                            ex.execute();*/
                            try {
                                routeCalculation(destinazioneSelezionata);
                            } catch (ExecutionException e) {
                                throw new RuntimeException(e);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }

                    }

                        /*} else {
                            if (gpsManager.getLocationNewOverlay() != null) {
                                map.getOverlays().remove(gpsManager.getLocationNewOverlay());

                                if (roadOverlay != null) {
                                    addRemoveLayerLine(false, roadOverlay);
                                }
                                map.invalidate();
                            }

                        }*/
                }
            }

            //Cosa fare se tolgo il gps
            @Override
            public void onProviderDisabled(String provider) {
                gpsManager.disableMyLocation();
            }

            //Cosa fare se abilito il gps
            @Override
            public void onProviderEnabled(String provider) {
                //In questo caso aggiorno la posizione
                Log.d("gpsenabled", "funge");
                gpsManager.enableMyLocation();
            }

            @Override
            public void onStatusChanged(String provider, int status,
                                        Bundle extras) {
            }
        });
    }

    //Va a calcolarti il percorso data la partenza e destinazione dagli spinner
    /*public class ExecuteTaskInBackGround extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            GeoPoint destinazione = controller.getGeoPoint(controller.getAppartenenza(destinazioneSelezionata));
            addRemoveLayerLine(false,roadOverlay);
            waypoints2.add(destinazione);
            Road road = roadManager.getRoad(waypoints2);
            roadOverlay = RoadManager.buildRoadOverlay(road);
            addRemoveLayerLine(true,roadOverlay);
            return null;
        }
    }*/
    public void routeCalculation(String destinazioneSelezionata) throws ExecutionException, InterruptedException {
        //non cancella ne il path ne il marker
        GeoPoint destinazione = controller.getGeoPoint(controller.getAppartenenza(destinazioneSelezionata));
        addRemoveLayerLine(false,roadOverlay);
        waypoints2.add(destinazione);
        roadOverlay = mViewModel.routeCalculation(roadManager, waypoints2, roadOverlay);
        addRemoveLayerLine(true,roadOverlay);
    }


    //Vado a mostrare il numero dei piani riguardante il singolo edificio che sto guardando
    private void updateItems(int nfloor) {
        // Aggiorna la lista degli elementi
        items.clear();

        for (int i = 0; i < nfloor; i++) {
            items.add("" + (i));
        }
        // Aggiorna l'adapter con i nuovi dati
        adapter.updateItems(items);
    }

    public void chooseTransport(){
        ///////// Per scegliere che tipologia di routing voglio
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        boolean bike= sharedPreferences.getBoolean("bike",false);
        boolean car= sharedPreferences.getBoolean("car",false);

        //Manager per calcolare in automatico il routing
        roadManager = new OSRMRoadManager(getContext(), "Prova indoor");

        if(car){
            ((OSRMRoadManager)roadManager).setMean(OSRMRoadManager.MEAN_BY_CAR);
            Log.d("proviamolo", " car");
        }else if(bike){
            ((OSRMRoadManager)roadManager).setMean(OSRMRoadManager.MEAN_BY_BIKE);
            Log.d("proviamolo", " bike");
        }else {
            ((OSRMRoadManager)roadManager).setMean(OSRMRoadManager.MEAN_BY_FOOT);
            Log.d("proviamolo", " walk");
        }
    }

    //Permessi per la memoria esterna
    /*public void getPermissionExternalStorage(){
        Log.d("entratoPermission", "ciao1");
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("entratoPermission", "ciao2");
            // Richiedi i permessi di scrittura
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_STORAGE);
        } else {
            Log.d("entratoPermission", "ciao3");
            // Hai già i permessi di scrittura
            // Puoi procedere con la modifica della cartella "emulated"
        }

    }*/

    private BroadcastReceiver networkChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Verifica lo stato della connessione e aggiorna il ViewModel
            boolean isConnected = isNetworkConnected();
            networkViewModel.setNetworkStatus(isConnected);
        }
    };

    private boolean isNetworkConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }


    public void initialiaze(String p, String d) throws ExecutionException, InterruptedException {
        if(p==null && d==null){
            if(aulaSelezionata!=null){
                addRemoveMarker(false,aulaSelezionata);
            }
            if(overlay!=null){
                map.getOverlayManager().remove(overlay);
            }
           if(roadOverlay!=null){
               addRemoveLayerLine(false, roadOverlay);
           }

        }else if(p!=null && d!=null){
            waypoints2 = new ArrayList<>();
            waypoints2.add(controller.getGeoPoint(controller.getAppartenenza(p)));
            ////
            gpsManager.disableFollowLocation();
            mapController.animateTo(controller.getGeoPoint(d));
            mapController.setZoom(18);
            addRemoveMarker(false,aulaSelezionata);
            aulaSelezionata=null;
            GeoPoint point = controller.getGeoPoint(d);
            //creo il marker per poter segnalare il posto
            aulaSelezionata=new Marker(map);
            aulaSelezionata.setPosition(point);
            aulaSelezionata.setTitle("Nome del luogo");
            aulaSelezionata.setSubDescription("Piano 1");
            aulaSelezionata.setIcon(getResources().getDrawable(R.drawable.baseline_heart_broken_24));
            addRemoveMarker(true,aulaSelezionata);
            if (controller.getFloor(d) != null) {
                //Potrei non essere sopra l'edificio e quindi non sapere a che edificio
                //mi stia riferendo, l'unica cosa che posso fare è andare a vedere se è associato
                // a qualche edificio,
                getFloorEdificio(controller.getFloor(d), true,d);

            } else {
                if (overlay != null) {
                    map.getOverlayManager().remove(overlay);
                }
                map.invalidate();
            }
            routeCalculation(d);

        }else if(d!=null){
            pointDestination(d);
        }
    }

    public void calculateExternalRouting(){
        Log.d("CicloDiVita","CLICK");
        // Visto che sono state selezionate vado a creare una lista di punti che poi verrà trasformata
        // in una strada da dare in pasto al RoadManager
        Log.d("partenzaSele", partenzaSelezionata);
        waypoints2 = new ArrayList<GeoPoint>();
        ////
        //In questo caso devo controllare che lo spinner abbia selezionato la posizioneAttuale così
        //da poter capire da dove parte, in questo caso dal gps
        if(partenzaSelezionata.equals(getString(R.string.posizioneAttuale))){
            if(isNetworkConnected()) {
                posizioneAttuale = true;
                //controllo che ci sia il gps
                if (gpsManager.gpsEnabled()) {
                    getPosizioneAttuale();
                } else {
                    showAlertMessageLocationDisabled();
                }
            }
        }else{
            posizioneAttuale = false;
            waypoints2.add(controller.getGeoPoint(partenzaSelezionata));
                        /*if(!isNetworkConnected() && partenzaSelezionata.equals(getString(R.string.posizioneAttuale))){
                            Toast.makeText(getContext(), "You need connection for routing", Toast.LENGTH_LONG);
                            showAlertMessageConnectionDisabled();
                            if (gpsManager.gpsEnabled()) {
                                getPosizioneAttuale();
                            } else {
                                showAlertMessageLocationDisabled();
                            }
                        }*/
        }
        if(isNetworkConnected()){
            try {
                routeCalculation(destinazioneSelezionata);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }else{
            showAlertMessageConnectionDisabled();
        }

                    /*// In questo caso vado ad aggiungere un controllo poichè così facendo riconosco
                    //quale destinazioni sono delle aule, e quindi se lo sono mi va a mostrare l'aula
                    // scelta tramite il marker
                    if(controller.getFloor(destinazioneSelezionata) != null){
                        //prendo il punto dove devo mettere il marker
                        getFloorEdificio(controller.getFloor(destinazioneSelezionata), true);

                    }else{
                        addRemoveMarker(true, aulaSelezionata);
                        if (overlay != null) {
                            map.getOverlayManager().remove(overlay);
                        }
                        map.invalidate();
                    }*/
                    /*FragmentOSM.ExecuteTaskInBackGround ex = new FragmentOSM.ExecuteTaskInBackGround();
                    ex.execute();*/

    }

    public void pointDestination(String destinazione){
        gpsManager.disableFollowLocation();
        mapController.animateTo(controller.getGeoPoint(destinazione));
        mapController.setZoom(18);
        addRemoveMarker(false,aulaSelezionata);
        aulaSelezionata=null;
        GeoPoint point = controller.getGeoPoint(destinazione);
        //creo il marker per poter segnalare il posto
        aulaSelezionata=new Marker(map);
        aulaSelezionata.setPosition(point);
        aulaSelezionata.setTitle("Nome del luogo");
        aulaSelezionata.setSubDescription("Piano 1");
        aulaSelezionata.setIcon(getResources().getDrawable(R.drawable.baseline_heart_broken_24));
        addRemoveMarker(true,aulaSelezionata);

        if (controller.getFloor(destinazione) != null) {
            //Potrei non essere sopra l'edificio e quindi non sapere a che edificio
            //mi stia riferendo, l'unica cosa che posso fare è andare a vedere se è associato
            // a qualche edificio,
            getFloorEdificio(controller.getFloor(destinazione), true, destinazione);

        } else {
            if (overlay != null) {
                map.getOverlayManager().remove(overlay);
            }
            map.invalidate();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        getActivity().registerReceiver(networkChangeReceiver, intentFilter);
        //la prima volta deve fare l'initialiaze quando gli arrivano tutti gli edifici, mentre la seconda volta dovrà ancora farlo
        //ma quando fa il resume
        if(primavolta) {
            SharedPreferences sharedPreferences = getActivity().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
            String destinazione = sharedPreferences.getString("destinazione", null);
            String partenza = sharedPreferences.getString("partenza", null);
            /////////////////////////////////////////////////////
            try {
                initialiaze(partenza, destinazione);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        primavolta = true;
        gpsManager.enableMyLocation();
        map.onResume();
        Log.d("CicloDiVita","RESUME");
    }

    @Override
    public void onPause() {
        super.onPause();
        gpsManager.disableMyLocation();
        map.onPause();
        SharedPreferences sharedPreferences = getContext().getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        if(edificioScoperto != null){
            editor.putString("edificio", edificioScoperto);
        }else{
            editor.putString("edificio", null);
        }

        if(TextUtils.isEmpty(spinnerDestinazione.getText())){
            //questo serve per mettere il punto di arrivo
            editor.putString("destinazione", null);
        }else{
            editor.putString("destinazione", destinazioneSelezionata);
        }

        if(TextUtils.isEmpty(spinnerPartenza.getText())){
            editor.putString("partenza", null);
        }else{
            editor.putString("partenza", partenzaSelezionata);
        }

        editor.apply();
        Log.d("CicloDiVita","onPause");
        Log.d("CicloDiVita",""+partenzaSelezionata);
        Log.d("CicloDiVita",""+destinazioneSelezionata);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        gpsManager.disableMyLocation();
        map.onDetach();
        getActivity().unregisterReceiver(networkChangeReceiver);
        Log.d("CicloDiVita","DESTROY");
    }

    @Override
    public void onStop() {
        super.onStop();
        gpsManager.disableMyLocation();
        Log.d("CicloDiVita","STOP");
    }
}
