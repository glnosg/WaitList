package com.example.android.waitlist;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.example.android.waitlist.data.WaitlistContract;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by pawel on 10.01.18.
 */

public class UndoHandler {

    // Constant defining number of enabled undos
    private static final int HOW_MANY_UNDOS_ENABLED = 5;

    // Database from which records are deleted
    private SQLiteDatabase mDb;
    // Flag defining current state of undo button
    private boolean isUndoButtonEnabled = false;

    // Holds records, which need to be deleted from database before undo operation,
    // and inserted back after it
    private List<ContentValues> mRecordsToReplace;
    // List of columns from table
    private String[] mProjection = {
            WaitlistContract.WaitlistEntry._ID,
            WaitlistContract.WaitlistEntry.COLUMN_GUEST_NAME,
            WaitlistContract.WaitlistEntry.COLUMN_PARTY_SIZE,
            WaitlistContract.WaitlistEntry.COLUMN_TIMESTAMP
    };
    // Holds five last deleted records
    private List<ContentValues> mLastFiveDeletedRecords = new ArrayList<>();
    // Holds IDs of five last deleted records
    private List<Integer> mLastFiveDeletedIDs = new ArrayList<>();

    /**
     *
     * @param db - Database on which UndoHandler will operate
     */
    public UndoHandler(SQLiteDatabase db) {
        mDb = db;
    }

    // Returns true if UndoButton is enabled, and false if it's not
    public boolean getUndoButtonState() {
        return isUndoButtonEnabled;
    }

    // Method triggered when user removes data from db
    // Saves deleted record int mLastFiveDeletedRecords list
    // Saves deleted record's ID in mLastFiveDeletedIDs list
    // Sets undo button state
    public void onDelete(int id) {

        Cursor cursor = mDb.query(
                WaitlistContract.WaitlistEntry.TABLE_NAME,
                mProjection,
                WaitlistContract.WaitlistEntry._ID + "=?",
                new String[] {Integer.toString(id)},
                null,
                null,
                null
        );

        try {
            if (cursor != null && cursor.moveToFirst()) {

                int idColumnIndex = cursor.getColumnIndex(WaitlistContract.WaitlistEntry._ID);
                int nameColumnIndex = cursor.getColumnIndex(WaitlistContract.WaitlistEntry.COLUMN_GUEST_NAME);
                int sizeColumnIndex = cursor.getColumnIndex(WaitlistContract.WaitlistEntry.COLUMN_PARTY_SIZE);
                int timestampColumnIndex = cursor.getColumnIndex(WaitlistContract.WaitlistEntry.COLUMN_TIMESTAMP);

                int currentID = cursor.getInt(idColumnIndex);
                String currentName = cursor.getString(nameColumnIndex);
                String currentSize = cursor.getString(sizeColumnIndex);
                String currentTimestamp = cursor.getString(timestampColumnIndex);

                ContentValues lastDeleted = new ContentValues();

                lastDeleted.put(WaitlistContract.WaitlistEntry._ID, currentID);
                lastDeleted.put(WaitlistContract.WaitlistEntry.COLUMN_GUEST_NAME, currentName);
                lastDeleted.put(WaitlistContract.WaitlistEntry.COLUMN_PARTY_SIZE, currentSize);
                lastDeleted.put(WaitlistContract.WaitlistEntry.COLUMN_TIMESTAMP, currentTimestamp);

                addLastDeletedToList(lastDeleted, id);
                setUndoButtonState();
            }

        } finally {
            cursor.close();
        }
    }

    // Method triggered when users clicks undo button
    // Saves records with indexes higher than index of item which is going to be recovered,
    // in mRecordsToReplace list
    // Removes them from database
    // Inserts deleted record back to db
    // Inserts elements stored in mRecordsToReplace list after it
    // Sets undo button state
    public void onUndo() {

        mRecordsToReplace = new ArrayList<>();
        mRecordsToReplace.add(getLastDeletedRecord());

        Cursor cursor = mDb.query(
                WaitlistContract.WaitlistEntry.TABLE_NAME,
                mProjection,
                WaitlistContract.WaitlistEntry._ID + ">?",
                new String[] {Integer.toString(getLastDeletedID())},
                null,
                null,
                null
        );

        try {
            if (cursor != null && cursor.moveToFirst()) {

                int idColumnIndex = cursor.getColumnIndex(WaitlistContract.WaitlistEntry._ID);
                int nameColumnIndex = cursor.getColumnIndex(WaitlistContract.WaitlistEntry.COLUMN_GUEST_NAME);
                int sizeColumnIndex = cursor.getColumnIndex(WaitlistContract.WaitlistEntry.COLUMN_PARTY_SIZE);
                int timestampColumnIndex = cursor.getColumnIndex(WaitlistContract.WaitlistEntry.COLUMN_TIMESTAMP);

                do {
                    int currentID = cursor.getInt(idColumnIndex);
                    String currentName = cursor.getString(nameColumnIndex);
                    String currentSize = cursor.getString(sizeColumnIndex);
                    String currentTimestamp = cursor.getString(timestampColumnIndex);

                    ContentValues currentCv = new ContentValues();

                    currentCv.put(WaitlistContract.WaitlistEntry._ID, currentID);
                    currentCv.put(WaitlistContract.WaitlistEntry.COLUMN_GUEST_NAME, currentName);
                    currentCv.put(WaitlistContract.WaitlistEntry.COLUMN_PARTY_SIZE, currentSize);
                    currentCv.put(WaitlistContract.WaitlistEntry.COLUMN_TIMESTAMP, currentTimestamp);

                    mRecordsToReplace.add(currentCv);

                } while (cursor.moveToNext());
            }

        } finally {
            cursor.close();
        }

        try
        {
            mDb.beginTransaction();
            mDb.delete (
                    WaitlistContract.WaitlistEntry.TABLE_NAME,
                    WaitlistContract.WaitlistEntry._ID + ">?",
                    new String[] {Integer.toString(getLastDeletedID())});

            for(ContentValues c: mRecordsToReplace){
                mDb.insert(WaitlistContract.WaitlistEntry.TABLE_NAME, null, c);
            }
            mDb.setTransactionSuccessful();
            removeLastDeleted();
            setUndoButtonState();
        }
        catch (SQLException e) {
            e.printStackTrace();
        }
        finally
        {
            mDb.endTransaction();
        }
    }

    // Handles adding last deleted element to the appropriate lists
    private void addLastDeletedToList(ContentValues record, Integer id) {

        if (mLastFiveDeletedRecords.size() < HOW_MANY_UNDOS_ENABLED) {

            mLastFiveDeletedRecords.add(record);
            mLastFiveDeletedIDs.add(id);

        } else if (mLastFiveDeletedRecords.size() >= HOW_MANY_UNDOS_ENABLED) {

            mLastFiveDeletedRecords.remove(0);
            mLastFiveDeletedIDs.remove(0);

            mLastFiveDeletedRecords.add(record);
            mLastFiveDeletedIDs.add(id);
        }
    }

    // Returns last deleted record
    private ContentValues getLastDeletedRecord() {
        int sizeOfList = mLastFiveDeletedRecords.size();
        ContentValues lastDeleted = mLastFiveDeletedRecords.get(sizeOfList - 1);

        return lastDeleted;
    }

    // Returns ID of last deleted record
    private Integer getLastDeletedID() {
        int sizeOfList = mLastFiveDeletedIDs.size();

        Integer lastDeletedID = mLastFiveDeletedIDs.get(sizeOfList - 1);

        return lastDeletedID;
    }

    // Removes the eldest ID and record from mLastFiveDeletedIDs and mLastFiveDeletedRecords lists
    private void removeLastDeleted() {
        int sizeOfList = mLastFiveDeletedRecords.size();

        mLastFiveDeletedRecords.remove(sizeOfList - 1);
        mLastFiveDeletedIDs.remove(sizeOfList - 1);
    }

    // Handles setting undo button's state
    // Sets state do disabled when there's nothing more to undo
    // (there's no more records in mLastFiveDeletedRecords list)
    private void setUndoButtonState() {
        if (mLastFiveDeletedRecords.isEmpty()) {
            isUndoButtonEnabled = false;
        } else {
            isUndoButtonEnabled = true;
        }
    }
}
