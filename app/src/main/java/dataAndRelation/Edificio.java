package dataAndRelation;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.example.osmdroidex2.Graph;
import com.example.osmdroidex2.GraphTypeConverter;
import com.example.osmdroidex2.MapTypeConverter;
import com.example.osmdroidex2.Node;

import org.osmdroid.util.GeoPoint;

import java.util.HashMap;
import java.util.Map;

@Entity(tableName = "edificio_universita")
@TypeConverters({GeoPointConverter.class, GraphTypeConverter.class, BitMapConverter.class, MapTypeConverter.class})
public class Edificio {

    //nome dell'edificio
    @PrimaryKey
    @NonNull
    String nomeEdificio;
    //4 punti dove mettere l'immagine;
    //devo fare un converter da geopoint a string, oppure a arraylist
    GeoPoint left_below;
    GeoPoint left_up;
    GeoPoint right_up;
    GeoPoint right_down;
    //dove sta la posizione dell'edificio
    GeoPoint posizione;
    int numeroFloor;

    // Conversione di graph0 in formato stringa
    Graph graph0;

    // Conversione di graph1 in formato stringa
    Graph graph1;

    private Map<Character, Graph> piani;

    double oneMeter;

    //Aule 1 e 2 con tutte le informazioni a riguardo
    /*@Relation(parentColumn = "nomeEdificio", entityColumn = "nomeEdificio")
    public List<Aula> aule;*/

    public Edificio(GeoPoint left_below, GeoPoint left_up,GeoPoint right_up , GeoPoint right_down, String nomeEdificio, GeoPoint posizione, int numeroFloor, Graph graph0, Graph graph1, double oneMeter){
        this.nomeEdificio=nomeEdificio;
        this.left_below=left_below;
        this.left_up=left_up;
        this.right_up=right_up;
        this.right_down=right_down;
        this.posizione=posizione;
        this.numeroFloor=numeroFloor;

        this.graph1 = graph1;
        this.graph0 = graph0;

        if(graph0 != null && graph1 != null){
            piani = new HashMap<>();
            piani.put('T', graph0);
            piani.put('1', graph1);
        }

        this.oneMeter = oneMeter;

    }


    public String getNomeEdificio() {
        return nomeEdificio;
    }

    public void setNomeEdificio(String nomeEdificio) {
        this.nomeEdificio = nomeEdificio;
    }

    public GeoPoint getPosizione() {
        return posizione;
    }

    public void setPosizione(GeoPoint posizione) {
        this.posizione = posizione;
    }

    public int getNumeroFloor() {
        return numeroFloor;
    }

    public void setNumeroFloor(int numeroFloor) {
        this.numeroFloor = numeroFloor;
    }

    public GeoPoint getLeft_below() {
        return left_below;
    }

    public void setLeft_below(GeoPoint left_below) {
        this.left_below = left_below;
    }

    public GeoPoint getLeft_up() {
        return left_up;
    }

    public void setLeft_up(GeoPoint left_up) {
        this.left_up = left_up;
    }

    public GeoPoint getRight_up() {
        return right_up;
    }

    public void setRight_up(GeoPoint right_up) {
        this.right_up = right_up;
    }

    public GeoPoint getRight_down() {
        return right_down;
    }

    public void setRight_down(GeoPoint right_down) {
        this.right_down = right_down;
    }

    public Graph getGraph0(){
        return graph0;
    }

    public Graph getGraph1() {
        return graph1;
    }

    public Map getPiani(){
        return piani;
    }

    public void setPiani(Map<Character, Graph> piani) {
        this.piani = piani;
    }

    public Graph getGraph(char piano){
        return piani.get(piano);
    }

    public double getOneMeter() {
        return oneMeter;
    }

}