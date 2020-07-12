package de.dennisguse.opentracks.io.file.importer;

import android.content.Context;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.dennisguse.opentracks.content.data.Track;
import de.dennisguse.opentracks.content.data.TrackPoint;
import de.dennisguse.opentracks.content.data.Waypoint;
import de.dennisguse.opentracks.content.provider.ContentProviderUtils;
import de.dennisguse.opentracks.io.file.TrackFileFormat;
import de.dennisguse.opentracks.io.file.exporter.TrackExporter;
import de.dennisguse.opentracks.stats.TrackStatistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Export a track to {@link TrackFileFormat} and verify that the import is identical.
 * <p>
 * TODO: test ignores {@link TrackStatistics} for now.
 */
@RunWith(JUnit4.class)
public class ExportImportTest {

    private static final String TAG = ExportImportTest.class.getSimpleName();

    private final Context context = ApplicationProvider.getApplicationContext();

    private final ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);

    private static final String TRACK_ICON = "the track icon";
    private static final String TRACK_CATEGORY = "the category";
    private static final String TRACK_DESCRIPTION = "the description";

    private static final double INITIAL_LATITUDE = 37.0;
    private static final double INITIAL_LONGITUDE = -57.0;
    private static final double ALTITUDE_INTERVAL = 2.5;

    private final List<Waypoint> waypoints = new ArrayList<>();
    private final List<TrackPoint> trackPoints = new ArrayList<>();

    private long importTrackId;
    private final long trackId = System.currentTimeMillis();

    @Before
    public void setUp() {
        Pair<Track, TrackPoint[]> pair = createTestTrack();
        pair.first.setId(trackId);
        contentProviderUtils.insertTrack(pair.first);
        contentProviderUtils.bulkInsertTrackPoint(pair.second, pair.first.getId());

        trackPoints.clear();
        trackPoints.addAll(Arrays.asList(pair.second));

        for (int i = 0; i < 3; i++) {
            Waypoint waypoint = new Waypoint(pair.second[i].getLocation());
            waypoint.setName("the waypoint " + i);
            waypoint.setDescription("the waypoint description " + i);
            waypoint.setCategory("the waypoint category" + i);
            waypoint.setIcon("the waypoing icon" + i);
            waypoint.setPhotoUrl("the photo url" + i);
            waypoint.setTrackId(trackId);
            contentProviderUtils.insertWaypoint(waypoint);

            waypoints.add(waypoint);
        }

        assertEquals(waypoints.size(), contentProviderUtils.getWaypointCount(trackId));
    }

    @After
    public void tearDown() {
        contentProviderUtils.deleteTrack(context, trackId);
        contentProviderUtils.deleteTrack(context, importTrackId);
    }

    @LargeTest
    @Test
    public void kml_with_trackdetail() {
        // given
        Track track = contentProviderUtils.getTrack(trackId);

        TrackFileFormat trackFileFormat = TrackFileFormat.KML_WITH_TRACKDETAIL;
        TrackExporter trackExporter = trackFileFormat.newTrackExporter(context, new Track[]{track});

        // when
        // 1. export
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        trackExporter.writeTrack(context, outputStream);

        // 2. import
        InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        AbstractFileTrackImporter trackImporter = new KmlFileTrackImporter(context, -1L);
        importTrackId = trackImporter.importFile(inputStream);

        // then
        // 1. track
        Track importedTrack = contentProviderUtils.getTrack(importTrackId);
        assertNotNull(importedTrack);
        assertEquals(track.getCategory(), importedTrack.getCategory());
        assertEquals(track.getDescription(), importedTrack.getDescription());
        assertEquals(track.getName(), importedTrack.getName());
        assertEquals(track.getIcon(), importedTrack.getIcon());

        // 2. waypoints
        assertWaypoints();

        // 3. trackpoints
        assertTrackpoints(false, false, false);
    }

    @LargeTest
    @Test
    public void kml_with_trackdetail_and_sensordata() {
        // given
        Track track = contentProviderUtils.getTrack(trackId);

        TrackFileFormat trackFileFormat = TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA;
        TrackExporter trackExporter = trackFileFormat.newTrackExporter(context, new Track[]{track});

        // when
        // 1. export
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        trackExporter.writeTrack(context, outputStream);

        // 2. import
        InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        AbstractFileTrackImporter trackImporter = new KmlFileTrackImporter(context, -1L);
        importTrackId = trackImporter.importFile(inputStream);

        // then
        // 1. track
        Track importedTrack = contentProviderUtils.getTrack(importTrackId);
        assertNotNull(importedTrack);
        assertEquals(track.getCategory(), importedTrack.getCategory());
        assertEquals(track.getDescription(), importedTrack.getDescription());
        assertEquals(track.getName(), importedTrack.getName());
        assertEquals(track.getIcon(), importedTrack.getIcon());

        // 2. waypoints
        assertWaypoints();

        // 3. trackpoints
        assertTrackpoints(true, true, true);
    }

    @LargeTest
    @Test
    public void gpx() {
        // given
        Track track = contentProviderUtils.getTrack(trackId);

        TrackFileFormat trackFileFormat = TrackFileFormat.GPX;
        TrackExporter trackExporter = trackFileFormat.newTrackExporter(context, new Track[]{track});

        // when
        // 1. export
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        trackExporter.writeTrack(context, outputStream);

        // 2. import
        InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        AbstractFileTrackImporter trackImporter = new GpxFileTrackImporter(context, contentProviderUtils);
        importTrackId = trackImporter.importFile(inputStream);

        // then
        // 1. track
        Track trackImported = contentProviderUtils.getTrack(importTrackId);
        assertNotNull(trackImported);
        assertEquals(track.getCategory(), trackImported.getCategory());
        assertEquals(track.getDescription(), trackImported.getDescription());
        assertEquals(track.getName(), trackImported.getName());

        //TODO exporting and importing a track icon is not yet supported by GpxTrackWriter.
        //assertEquals(track.getIcon(), trackImported.getIcon());

        // 2. waypoints
        assertWaypoints();

        // 3. trackpoints
        assertTrackpoints(false, false, false);
    }

    private void assertWaypoints() {
        assertEquals(waypoints.size(), contentProviderUtils.getWaypointCount(importTrackId));

        List<Waypoint> importedWaypoints = contentProviderUtils.getWaypoints(importTrackId);
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint waypoint = waypoints.get(i);
            Waypoint importedWaypoint = importedWaypoints.get(i);
            assertEquals(waypoint.getCategory(), importedWaypoint.getCategory());
            assertEquals(waypoint.getDescription(), importedWaypoint.getDescription());
            // assertEquals(waypoint.getIcon(), importedWaypoint.getIcon()); // TODO for KML
            assertEquals(waypoint.getName(), importedWaypoint.getName());
            assertEquals("", importedWaypoint.getPhotoUrl());

            assertEquals(waypoint.getLocation().getLatitude(), importedWaypoint.getLocation().getLatitude(), 0.001);
            assertEquals(waypoint.getLocation().getLongitude(), importedWaypoint.getLocation().getLongitude(), 0.001);
            assertEquals(waypoint.getLocation().getAltitude(), importedWaypoint.getLocation().getAltitude(), 0.001);
        }
    }

    private void assertTrackpoints(boolean verifyPower, boolean verifyHeartrate, boolean verifyCadence) {
        List<TrackPoint> importedTrackPoints = contentProviderUtils.getTrackPoints(importTrackId);
        assertEquals(trackPoints.size(), importedTrackPoints.size());

        for (int i = 0; i < trackPoints.size(); i++) {
            TrackPoint trackPoint = trackPoints.get(i);
            TrackPoint importedTrackPoint = importedTrackPoints.get(i);

            assertEquals(trackPoint.getTime(), importedTrackPoint.getTime(), 0.01);

            // TODO Not exported for GPX/KML
            //   assertEquals(trackPoint.getAccuracy(), importedTrackPoint.getAccuracy(), 0.01);

            assertEquals(trackPoint.getLatitude(), importedTrackPoint.getLatitude(), 0.001);
            assertEquals(trackPoint.getLongitude(), importedTrackPoint.getLongitude(), 0.001);
            assertEquals(trackPoint.getAltitude(), importedTrackPoint.getAltitude(), 0.001);
            assertEquals(trackPoint.getSpeed(), importedTrackPoint.getSpeed(), 0.001);
            if (verifyHeartrate) {
                assertEquals(trackPoint.getHeartRate_bpm(), importedTrackPoint.getHeartRate_bpm(), 0.01);
            }
            if (verifyCadence) {
                assertEquals(trackPoint.getCyclingCadence_rpm(), importedTrackPoint.getCyclingCadence_rpm(), 0.01);
            }
            if (verifyPower) {
                assertEquals(trackPoint.getPower(), importedTrackPoint.getPower(), 0.01);
            }
        }
    }

    /**
     * Generates a track with 3 segments each containing 10 valid {@link TrackPoint}s.
     */
    private Pair<Track, TrackPoint[]> createTestTrack() {
        Track track = new Track();
        track.setIcon(TRACK_ICON);
        track.setCategory(TRACK_CATEGORY);
        track.setDescription(TRACK_DESCRIPTION);
        track.setName("Test: " + trackId);

        ArrayList<TrackPoint> trackPoints = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            switch (i) {
                // Signal lost / distance to great
                case 10:
                    trackPoints.add(TrackPoint.createPauseWithTime(i + 1));
                    break;

                // Pause / Resume
                case 20:
                    trackPoints.add(TrackPoint.createPauseWithTime(i + 1));
                case 21:
                    trackPoints.add(TrackPoint.createResumeWithTime(i + 1));
                    break;

                default:
                    trackPoints.add(createTrackPoint(i));
            }
        }

        return new Pair<>(track, trackPoints.toArray(new TrackPoint[0]));
    }

    public static TrackPoint createTrackPoint(int i) {
        TrackPoint trackPoint = new TrackPoint();
        trackPoint.setLatitude(INITIAL_LATITUDE + (double) i / 10000.0);
        trackPoint.setLongitude(INITIAL_LONGITUDE - (double) i / 10000.0);
        trackPoint.setAccuracy((float) i / 100.0f);
        trackPoint.setAltitude(i * ALTITUDE_INTERVAL);
        trackPoint.setTime(i + 1);
        trackPoint.setSpeed(5f + (i / 10));

        trackPoint.setHeartRate_bpm(100f + i);
        trackPoint.setCyclingCadence_rpm(200f + i);
        trackPoint.setCyclingCadence_rpm(300f + i);
        trackPoint.setPower(400f + i);
        return trackPoint;
    }
}