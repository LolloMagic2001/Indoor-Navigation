package dataAndRelation;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface EdificioDao {

    @Insert
    void insertEdificio(Edificio u14);

    @Update
    void updateEdificio(Edificio u14);

    @Delete
    void delete(Edificio u14);

    /*@Query("DELETE FROM edificio_universita")
    void deleteAllEdifici();*/

    @Query("SELECT * FROM edificio_universita ORDER BY nomeEdificio DESC")
     LiveData<List<Edificio>> getAllEdificio();


    @Query("SELECT * FROM edificio_universita WHERE nomeEdificio = :edificio")
     LiveData<Edificio> getEdificioObj(String edificio);

    @Query("SELECT * FROM edificio_universita WHERE nomeEdificio = :edificio")
    Edificio getEdificio(String edificio);

}
