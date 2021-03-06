/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package de.dennisguse.opentracks.services;

import android.content.ContentProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.content.data.Track;
import de.dennisguse.opentracks.content.data.TrackPoint;
import de.dennisguse.opentracks.content.data.TrackPointsColumns;
import de.dennisguse.opentracks.content.data.Waypoint;
import de.dennisguse.opentracks.content.provider.ContentProviderUtils;
import de.dennisguse.opentracks.content.provider.CustomContentProvider;
import de.dennisguse.opentracks.stats.TrackStatistics;
import de.dennisguse.opentracks.util.PreferencesUtils;

/**
 * Tests for the track recording service.
 *
 * @author Bartlomiej Niechwiej
 * <p>
 * //TODO ATTENTION: This tests deletes all stored tracks in the database.
 * So, if it is executed on a real device, data might be lost.
 */
@RunWith(AndroidJUnit4.class)
public class TrackRecordingServiceTest {

    @Rule
    public final ServiceTestRule mServiceRule = ServiceTestRule.withTimeout(5, TimeUnit.SECONDS);

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION);

    @BeforeClass
    public static void preSetUp() {
        // Prepare looper for Android's message queue
        if (Looper.myLooper() == null) Looper.prepare();
    }


    private final Context context = ApplicationProvider.getApplicationContext();
    private ContentProviderUtils contentProviderUtils;

    private final Track.Id trackId = new Track.Id(Math.abs(new Random().nextLong()));

    static Intent createStartIntent(Context context) {
        return new Intent(context, TrackRecordingService.class);
    }

    @Before
    public void setUp() {
        // Set up the mock content resolver
        ContentProvider customContentProvider = new CustomContentProvider() {
        };
        customContentProvider.attachInfo(context, null);

        contentProviderUtils = new ContentProviderUtils(context);

        // Let's use default values.
        SharedPreferences sharedPreferences = PreferencesUtils.getSharedPreferences(context);
        sharedPreferences.edit().clear().commit();

        // Ensure that the database is empty before every test
        contentProviderUtils.deleteAllTracks(context);
    }

    @After
    public void tearDown() throws TimeoutException {
        // Reset service (if some previous test failed)
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(createStartIntent(context)));
        if (service.isRecording() || service.isPaused()) {
            service.endCurrentTrack();
        }

        // Ensure that the database is empty after every test
        contentProviderUtils.deleteAllTracks(context);
    }

    @SmallTest
    @Test
    public void testStartable() throws TimeoutException {
        mServiceRule.startService(createStartIntent(context));
        Assert.assertNotNull(mServiceRule.bindService(createStartIntent(context)));
    }

    @MediumTest
    @Test
    public void testBindable() throws TimeoutException {
        IBinder service = mServiceRule.bindService(createStartIntent(context));
        Assert.assertNotNull(service);
    }

    @MediumTest
    @Test
    public void testRecording_noTracks() throws Exception {
        // given
        List<Track> tracks = contentProviderUtils.getTracks();
        Assert.assertTrue(tracks.isEmpty());

        // when
        Intent startIntent = createStartIntent(context);
        mServiceRule.startService(startIntent);
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(startIntent));

        // then
        // Test if we start in no-recording mode by default.
        Assert.assertFalse(service.isRecording());
        Assert.assertNull(service.getRecordingTrackId());
    }

    @MediumTest
    @Test
    public void testRecording_oldTracks() throws Exception {
        // given
        createDummyTrack(trackId, -1L, false);

        // when
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(createStartIntent(context)));

        // then
        Assert.assertFalse(service.isRecording());
        Assert.assertNull(service.getRecordingTrackId());
    }

    @MediumTest
    @Test
    public void testRecording_serviceRestart_whileRecording() throws Exception {
        // given
        createDummyTrack(trackId, -1L, true);

        //when
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(createStartIntent(context)));

        // then
        Assert.assertTrue(service.isRecording());
    }

    @MediumTest
    @Test
    public void testRecording_pauseAndResume() throws Exception {
        // given
        createDummyTrack(trackId, -1L, true);
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(createStartIntent(context)));
        newTrackPoint(service);

        // when
        service.pauseCurrentTrack();

        // then
        Assert.assertEquals(2, contentProviderUtils.getTrackPoints(trackId).size());

        //when
        service.resumeTrack(trackId);
        newTrackPoint(service);

        // then
        Assert.assertTrue(service.isRecording());
        Assert.assertEquals(trackId, service.getRecordingTrackId());

        List<TrackPoint> trackPoints = contentProviderUtils.getTrackPoints(trackId);
        Assert.assertEquals(5, trackPoints.size());
        Assert.assertEquals(TrackPointsColumns.PAUSE_LATITUDE, trackPoints.get(1).getLatitude(), 0.01);
        Assert.assertEquals(TrackPointsColumns.PAUSE_LATITUDE, trackPoints.get(2).getLatitude(), 0.01);
        Assert.assertEquals(TrackPointsColumns.RESUME_LATITUDE, trackPoints.get(3).getLatitude(), 0.01);
    }

    @MediumTest
    @Test
    public void testRecording_resumeStoppedTrack() throws Exception {
        // given
        createDummyTrack(trackId, -1L, true);
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(createStartIntent(context)));
        newTrackPoint(service);
        service.endCurrentTrack();

        Assert.assertEquals(1, contentProviderUtils.getTrackPoints(trackId).size());

        // when
        service.resumeTrack(trackId);
        newTrackPoint(service);

        // then
        Assert.assertTrue(service.isRecording());
        Assert.assertEquals(trackId, service.getRecordingTrackId());

        List<TrackPoint> trackPoints = contentProviderUtils.getTrackPoints(trackId);
        Assert.assertEquals(4, trackPoints.size());
        Assert.assertEquals(TrackPointsColumns.PAUSE_LATITUDE, trackPoints.get(1).getLatitude(), 0.01);
        Assert.assertEquals(TrackPointsColumns.RESUME_LATITUDE, trackPoints.get(2).getLatitude(), 0.01);
    }

    @FlakyTest(detail = "Sometimes fails on CI.")
    @MediumTest
    @Test
    public void testRecording_orphanedRecordingTrack() throws Exception {
        // given
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(createStartIntent(context)));

        // when
        // Just set recording track to a bogus value.
        // Make sure that the service will not start recording and will clear the bogus track.
        PreferencesUtils.setLong(context, R.string.recording_track_id_key, 123L);

        // then
        Assert.assertFalse(service.isRecording());
        Assert.assertNull(service.getRecordingTrackId());
    }

    @MediumTest
    @Test
    public void testStartNewTrack_alreadyRecording() throws Exception {
        // given
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(createStartIntent(context)));
        service.startNewTrack();
        Assert.assertTrue(service.isRecording());

        Track.Id trackId = service.getRecordingTrackId();

        // when
        Track.Id newTrackId = service.startNewTrack();

        // then
        Assert.assertNull(newTrackId);

        Assert.assertEquals(trackId, PreferencesUtils.getRecordingTrackId(context));
        Assert.assertEquals(trackId, service.getRecordingTrackId());
    }

    @MediumTest
    @Test
    public void testEndCurrentTrack_noRecording() throws Exception {
        // given
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(createStartIntent(context)));
        Assert.assertFalse(service.isRecording());

        // when
        // Ending the current track when there is no recording should not result in any error.
        service.endCurrentTrack();

        // then
        Assert.assertFalse(PreferencesUtils.isRecording(context));
        Assert.assertNull(service.getRecordingTrackId());
    }

    @MediumTest
    @Test
    public void testInsertWaypointMarker_noRecordingTrack() throws Exception {
        // given
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(createStartIntent(context)));
        Assert.assertFalse(service.isRecording());

        // when
        Waypoint.Id waypointId = service.insertWaypoint(null, null, null, null);

        // then
        Assert.assertNull(waypointId);
    }

    @MediumTest
    @Test
    public void testInsertWaypointMarker_validWaypoint() throws Exception {
        // given
        TrackRecordingServiceInterface service = ((TrackRecordingServiceInterface) mServiceRule.bindService(createStartIntent(context)));
        service.startNewTrack();
        Assert.assertTrue(service.isRecording());
        newTrackPoint(service);
        Track.Id trackId = service.getRecordingTrackId();

        // when
        Waypoint.Id waypointId = service.insertWaypoint(null, null, null, null);

        // then
        Assert.assertNotEquals(-1L, waypointId);
        Waypoint wpt = contentProviderUtils.getWaypoint(waypointId);
        Assert.assertEquals(context.getString(R.string.marker_waypoint_icon_url), wpt.getIcon());
        Assert.assertEquals(context.getString(R.string.marker_name_format, 1), wpt.getName());
        Assert.assertEquals(trackId, wpt.getTrackId());
        Assert.assertEquals(0.0, wpt.getLength(), 0.01);
        Assert.assertNotNull(wpt.getLocation());

        service.endCurrentTrack();
    }

    private void addTrack(Track track, boolean isRecording) {
        Assert.assertTrue(track.getId().isValid());
        contentProviderUtils.insertTrack(track);
        Assert.assertEquals(track.getId(), contentProviderUtils.getTrack(track.getId()).getId());
        PreferencesUtils.setLong(context, R.string.recording_track_id_key, isRecording ? track.getId().getId() : PreferencesUtils.RECORDING_TRACK_ID_DEFAULT);
        PreferencesUtils.setBoolean(context, R.string.recording_track_paused_key, !isRecording);
    }

    // NOTE: Do not use to create a track that is currently recording.
    private void createDummyTrack(Track.Id id, long stopTime, boolean isRecording) {
        Track dummyTrack = new Track();
        dummyTrack.setId(id);
        dummyTrack.setName("Dummy Track");
        TrackStatistics trackStatistics = new TrackStatistics();
        trackStatistics.setStopTime_ms(stopTime);
        dummyTrack.setTrackStatistics(trackStatistics);
        addTrack(dummyTrack, isRecording);
    }

    private static void newTrackPoint(TrackRecordingServiceInterface trackRecordingService) throws InterruptedException {
        newTrackPoint(trackRecordingService, 45.0f, 35f, 5, 10, System.currentTimeMillis());
    }

    static void newTrackPoint(TrackRecordingServiceInterface trackRecordingService, double latitude, double longitude, float accuracy, long speed) throws InterruptedException {
        newTrackPoint(trackRecordingService, latitude, longitude, accuracy, speed, System.currentTimeMillis());
    }

    /**
     * Inserts a location and waits for 200ms.
     */
    private static void newTrackPoint(TrackRecordingServiceInterface trackRecordingService, double latitude, double longitude, float accuracy, long speed, long time) throws InterruptedException {
        Location location = new Location("gps");
        location.setLongitude(longitude);
        location.setLatitude(latitude);
        location.setAccuracy(accuracy);
        location.setSpeed(speed);
        location.setTime(time);
        location.setBearing(3.0f);
        TrackPoint trackPoint = new TrackPoint(location);
        int prefAccuracy = PreferencesUtils.getRecordingGPSAccuracy(ApplicationProvider.getApplicationContext());
        trackRecordingService.newTrackPoint(trackPoint, prefAccuracy);

        //TODO Needed?
        Thread.sleep(200);
    }
}
