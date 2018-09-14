package com.example.jeff9123.displaymap;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.migration.Migration;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

@Database(entities = {Location.class}, version = 2)
public abstract class LocationDatabase extends RoomDatabase {
    public abstract LocationDao locationDao();

    private static LocationDatabase INSTANCE;

    static LocationDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (LocationDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), LocationDatabase.class, "location_table")
                            .addMigrations(MIGRATION_1_2)
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static RoomDatabase.Callback sRoomDatabaseCallback =
    new RoomDatabase.Callback(){

        @Override
        public void onOpen (@NonNull SupportSQLiteDatabase db){
            super.onOpen(db);
            new PopulateDbAsync(INSTANCE).execute();
        }
    };

    private static class PopulateDbAsync extends AsyncTask<Void, Void, Void> {

        private final LocationDao mDao;

        PopulateDbAsync(LocationDatabase db) {
            mDao = db.locationDao();
        }

        @Override
        protected Void doInBackground(final Void... params) {
            mDao.deleteAll();
            return null;
        }
    }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("DELETE FROM location_table");
            database.execSQL("ALTER TABLE location_table "
                + " ADD COLUMN latitude REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE location_table "
                    + " ADD COLUMN longitude REAL NOT NULL DEFAULT 0");
        }
    };
}
