package com.proggroup.areasquarecalculator.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import com.proggroup.areasquarecalculator.data.Project;

import java.util.ArrayList;
import java.util.List;

public class ProjectHelper {
    public static final String CREATE_REQUEST = "create table " + Project.TABLE_NAME +
            " ( " + BaseColumns._ID + " integer primary key autoincrement, "
            + Project.IS_SIMPLE_MEASURE + " integer not null);";
    public static final String DROP_REQUEST = "drop table if exists" + Project.TABLE_NAME;

    private SQLiteDatabase writeDb;

    public ProjectHelper(SQLiteDatabase writeDb) {
        if(writeDb != null) {
            this.writeDb = writeDb;
        }
    }

    public void startInit() {
        addProject(true);
        addProject(false);
    }

    public void startInit(SQLiteDatabase writeDb) {
        this.writeDb = writeDb;
        startInit();
    }

    public Project addProject(boolean isSimpleMeasure) {
        ContentValues cv = new ContentValues(1);
        cv.put(Project.IS_SIMPLE_MEASURE, isSimpleMeasure ? 1 : 0);
        long id = writeDb.insert(Project.TABLE_NAME, null, cv);
        Project project = new Project();
        project.setIsSimpleMeasure(isSimpleMeasure);
        project.setId(id);
        return project;
    }

    /*public void deleteProject(Project project) {
        AvgPointHelper avgPointHelper = new AvgPointHelper(writeDb, project);
        for (long id : avgPointHelper.getAvgPoints()) {
            avgPointHelper.deleteAvgPoint(id);
        }
        writeDb.delete(Project.TABLE_NAME, BaseColumns._ID + " = ?", new String[]{project.getId() +
                ""});
    }*/

    public List<Project> getProjects() {
        Cursor cursor = writeDb.query(Project.TABLE_NAME, new String[]{BaseColumns._ID,
                Project
                .IS_SIMPLE_MEASURE}, null, null, null, null, null);

        if (cursor.moveToFirst()) {
            List<Project> res = new ArrayList<>(cursor.getCount());

            do {
                Project project = new Project();
                project.setId(cursor.getLong(0));
                project.setIsSimpleMeasure(cursor.getInt(1) == 1);

                res.add(project);
            } while (cursor.moveToNext());
            cursor.close();

            return res;
        } else {
            cursor.close();
            return new ArrayList<>(0);
        }
    }

    public void clear() {
        writeDb.delete(Project.TABLE_NAME, null, null);
    }
}
