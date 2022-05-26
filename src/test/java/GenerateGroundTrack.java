import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geotools.data.*;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.GeometryBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.map.FeatureLayer;
import org.geotools.map.Layer;
import org.geotools.map.MapContent;
import org.geotools.renderer.GTRenderer;
import org.geotools.renderer.lite.StreamingRenderer;
import org.geotools.styling.*;
import org.geotools.swing.JMapFrame;
import org.hipparchus.util.FastMath;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;
import org.opengis.style.ContrastMethod;
import seakers.orekit.analysis.Analysis;
import seakers.orekit.analysis.ephemeris.GroundTrackAnalysis;
import seakers.orekit.coverage.analysis.GroundEventAnalyzer;
import seakers.orekit.event.EventAnalysis;
import seakers.orekit.event.FieldOfViewEventAnalysis;
import seakers.orekit.event.GndStationEventAnalysis;
import seakers.orekit.object.CoverageDefinition;
import seakers.orekit.object.Satellite;
import seakers.orekit.object.Instrument;
import seakers.orekit.analysis.Record;
import seakers.orekit.object.communications.ReceiverAntenna;
import seakers.orekit.object.communications.TransmitterAntenna;
import seakers.orekit.object.fieldofview.NadirRectangularFOV;
import seakers.orekit.propagation.*;
import seakers.orekit.object.*;
import seakers.orekit.coverage.access.*;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import seakers.orekit.scenario.Scenario;
import seakers.orekit.util.OrekitConfig;
import seakers.planning.Observation;
import seakers.planning.SMDPPlanner;

import javax.imageio.ImageIO;

import static java.lang.Double.parseDouble;
import static org.apache.commons.io.FileUtils.getFile;
import static seakers.orekit.object.CommunicationBand.S;

public class GenerateGroundTrack {

    public static void main(String[] args) {

        // Orekit initialization needs
        OrekitConfig.init(4);
        String greedyPlannerFilePath = "./src/test/greedyPlannerOutput/";
        File orekitData = new File("./src/main/resources/orekitResources");
        DataProvidersManager manager = DataProvidersManager.getInstance();
        manager.addProvider(new DirectoryCrawler(orekitData));
        Level level = Level.ALL;
        Logger.getGlobal().setLevel(level);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(level);
        Logger.getGlobal().addHandler(handler);
        Frame earthFrame = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
        Frame inertialFrame = FramesFactory.getEME2000();
        BodyShape earthShape = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING, earthFrame);
        TimeScale utc = TimeScalesFactory.getUTC();
        AbsoluteDate startDate = new AbsoluteDate(2020, 1, 1, 10, 30, 00.000, utc);
        double mu = Constants.WGS84_EARTH_MU;

        // Initializing
        ArrayList<Satellite> imagers = new ArrayList<>();
        Collection<Instrument> ssPayload = new ArrayList<>();
        double ssCrossFOVRadians = Math.toRadians(30.0);
        double ssAlongFOVRadians = Math.toRadians(1.0);
        NadirRectangularFOV ssFOV = new NadirRectangularFOV(ssCrossFOVRadians,ssAlongFOVRadians,0.0,earthShape);
        Instrument ssImager = new Instrument("Smallsat imager", ssFOV, 100.0, 100.0);
        ssPayload.add(ssImager);
        int r = 1;
        int s = 1;
        for(int m = 0; m < r; m++) {
            for(int n = 0; n < s; n++) {
                int pu = 360 / (r*s);
                int delAnom = pu * r; //in plane spacing between satellites
                int delRAAN = pu * s; //node spacing
                int RAAN = delRAAN * m;
                int f = 1;
                int phasing = pu * f;
                int anom = (n * delAnom + phasing * m);
                Orbit ssOrbit = new KeplerianOrbit(6378000+705000, 0.0, FastMath.toRadians(98.2), 0.0, FastMath.toRadians(RAAN-45), FastMath.toRadians(anom), PositionAngle.MEAN, inertialFrame, startDate, mu);
//                TransmitterAntenna tx = new TransmitterAntenna(1.0,Collections.singleton(S));
//                ReceiverAntenna rx = new ReceiverAntenna(1.0,Collections.singleton(S));
                Satellite smallsat = new Satellite("smallsat"+m+n, ssOrbit, ssPayload);
                imagers.add(smallsat);
            }
        }
        double duration = 16.0;
        processGroundTracks(imagers,greedyPlannerFilePath+"groundtrack_terra_16days.shp",startDate,duration);

        plotResults(greedyPlannerFilePath);
    }

    private static Set<GndStation> initializeNEN(BodyShape earthShape) {
        Set<GndStation> gs = new HashSet<>();
        ReceiverAntenna singaporeRX = new ReceiverAntenna(10.0, Collections.singleton(S));
        TransmitterAntenna singaporeTX = new TransmitterAntenna(10.0, Collections.singleton(S));
        GeodeticPoint singapore = new GeodeticPoint(Math.toRadians(1),Math.toRadians(103),0.0);
        TopocentricFrame singaporeTF = new TopocentricFrame(earthShape,singapore,"Singapore NEN Station");
        GndStation singaporeGS = new GndStation(singaporeTF,singaporeRX,singaporeTX,Math.toRadians(10.0));
        gs.add(singaporeGS);
        ReceiverAntenna svalbardRX = new ReceiverAntenna(10.0, Collections.singleton(S));
        TransmitterAntenna svalbardTX = new TransmitterAntenna(10.0, Collections.singleton(S));
        GeodeticPoint svalbard = new GeodeticPoint(Math.toRadians(78),Math.toRadians(15),0.0);
        TopocentricFrame svalbardTF = new TopocentricFrame(earthShape,svalbard,"Svalbard NEN Station");
        GndStation svalbardGS = new GndStation(svalbardTF,svalbardRX,svalbardTX,Math.toRadians(10.0));
        gs.add(svalbardGS);
        ReceiverAntenna trollsatRX = new ReceiverAntenna(10.0, Collections.singleton(S));
        TransmitterAntenna trollsatTX = new TransmitterAntenna(10.0, Collections.singleton(S));
        GeodeticPoint trollsat = new GeodeticPoint(Math.toRadians(-72),Math.toRadians(2),0.0);
        TopocentricFrame trollsatTF = new TopocentricFrame(earthShape,trollsat,"Trollsat NEN Station");
        GndStation trollsatGS = new GndStation(trollsatTF,trollsatRX,trollsatTX,Math.toRadians(10.0));
        gs.add(trollsatGS);
        return gs;

    }

    public static double getIncidenceAngle(GeodeticPoint point, double riseTime, double setTime, AbsoluteDate startDate, Satellite satellite, Collection<Record<String>> groundTrack) {
        Map<Double, GeodeticPoint> sspMap = new HashMap<>();
        for (Record<String> ind : groundTrack) {
            String rawString = ind.getValue();
            AbsoluteDate date = ind.getDate();
            String[] splitString = rawString.split(",");
            double latitude = Double.parseDouble(splitString[0]);
            double longitude = Double.parseDouble(splitString[1]);
            GeodeticPoint ssp = new GeodeticPoint(Math.toRadians(latitude),Math.toRadians(longitude),0);
            double elapsedTime = date.durationFrom(startDate);
            sspMap.put(elapsedTime,ssp);
        }
        double[] times = linspace(riseTime,setTime,(int)(setTime-riseTime));
        if(times.length<2) {
            times = new double[]{riseTime, setTime};
        }

        double closestDist = 100000000000000000.0;
        for (double time : times) {
            double closestTime = 100 * 24 * 3600; // 100 days
            GeodeticPoint closestPoint = null;
            for (Double sspTime : sspMap.keySet()) {
                if (Math.abs(sspTime - time) < closestTime) {
                    closestTime = Math.abs(sspTime - time);
                    closestPoint = sspMap.get(sspTime);
                    double dist = Math.sqrt(Math.pow(LLAtoECI(closestPoint)[0] - LLAtoECI(point)[0], 2) + Math.pow(LLAtoECI(closestPoint)[1] - LLAtoECI(point)[1], 2) + Math.pow(LLAtoECI(closestPoint)[2] - LLAtoECI(point)[2], 2));
                    if (dist < closestDist) {
                        closestDist = dist;
                    }
                }
            }
        }
        return Math.atan2(closestDist,(satellite.getOrbit().getA()-6370000)/1000);
    }

    public static double[] LLAtoECI(GeodeticPoint point) {
        double re = 6370;
        double x = re * Math.cos(point.getLatitude()) * Math.cos(point.getLongitude());
        double y = re * Math.cos(point.getLatitude()) * Math.sin(point.getLongitude());
        double z = re * Math.sin(point.getLatitude());
        double[] result = {x,y,z};
        return result;
    }
    public static Collection<Record<String>> getGroundTrack(Orbit orbit, double duration, AbsoluteDate startDate) {
        OrekitConfig.init(16);
        TimeScale utc = TimeScalesFactory.getUTC();
        AbsoluteDate endDate = startDate.shiftedBy(duration*86400);

        Frame earthFrame = FramesFactory.getITRF(IERSConventions.IERS_2003, true);

        BodyShape earthShape = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING, earthFrame);
        ArrayList<Instrument> payload = new ArrayList<>();
        Satellite sat1 = new Satellite(orbit.toString(), orbit,  payload);
        Properties propertiesPropagator = new Properties();
        PropagatorFactory pf = new PropagatorFactory(PropagatorType.J2,propertiesPropagator);


        Collection<Analysis<?>> analyses = new ArrayList<>();
        double analysisTimeStep = 10;
        GroundTrackAnalysis gta = new GroundTrackAnalysis(startDate, endDate, analysisTimeStep, sat1, earthShape, pf);
        analyses.add(gta);
        Scenario scen = new Scenario.Builder(startDate, endDate, utc).
                analysis(analyses).name(orbit.toString()).propagatorFactory(pf).build();
        try {
            scen.call();
        } catch (Exception ex) {
            throw new IllegalStateException("Ground track scenario failed to complete.");
        }
        OrekitConfig.end();
        return gta.getHistory();
    }

    public static void processGroundTracks(ArrayList<Satellite> satellites, String filepath, AbsoluteDate startDate, double duration) {
        try {

            final SimpleFeatureType TYPE =
                    DataUtilities.createType(
                            "Location",
                            "the_geom:Point:srid=4326,"
                    );
            List<SimpleFeature> features = new ArrayList<>();
            GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
            for (Satellite satellite : satellites) {
                Collection<Record<String>> coll = getGroundTrack(satellite.getOrbit(), duration, startDate);
                for (Record<String> ind : coll) {
                    String rawString = ind.getValue();
                    String[] splitString = rawString.split(",");
                    double latitude = Double.parseDouble(splitString[0]);
                    double longitude = Double.parseDouble(splitString[1]);
                    Point point = geometryFactory.createPoint(new Coordinate(latitude, longitude));
                    featureBuilder.add(point);
                    SimpleFeature feature = featureBuilder.buildFeature(null);
                    features.add(feature);
                }
            }
            File newFile = getFile(new File(filepath));
            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
            Map<String, Serializable> params = new HashMap<>();
            params.put("url", newFile.toURI().toURL());
            params.put("create spatial index", Boolean.TRUE);
            ShapefileDataStore newDataStore =
                    (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
            newDataStore.createSchema(TYPE);
            Transaction transaction = new DefaultTransaction("create");
            String typeName = newDataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
            if (featureSource instanceof SimpleFeatureStore) {
                SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
                SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
                featureStore.setTransaction(transaction);
                try {
                    featureStore.addFeatures(collection);
                    transaction.commit();
                } catch (Exception problem) {
                    problem.printStackTrace();
                    transaction.rollback();
                } finally {
                    transaction.close();
                }
            } else {
                System.out.println(typeName + " does not support read/write access");
                System.exit(1);
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    public static void processGroundTracksXD(ArrayList<Satellite> satellites, String filepath, AbsoluteDate startDate, double duration) {
        try {

            SimpleFeatureType TYPE = DataUtilities.createType("test", "line", "the_geom:LineString");
            List<SimpleFeature> features = new ArrayList<>();
            GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
            ArrayList<Coordinate> coordinates = new ArrayList<>();
            for (Satellite satellite : satellites) {
                Collection<Record<String>> coll = getGroundTrack(satellite.getOrbit(), duration, startDate);
                for (Record<String> ind : coll) {
                    String rawString = ind.getValue();
                    String[] splitString = rawString.split(",");
                    double latitude = Double.parseDouble(splitString[0]);
                    double longitude = Double.parseDouble(splitString[1]);
                    Point point = geometryFactory.createPoint();
                    coordinates.add(new Coordinate(longitude,latitude));
                    featureBuilder.add(point);
                    SimpleFeature feature = featureBuilder.buildFeature(null);
                    //features.add(feature);
                }
            }
            ArrayList<LineSegment> lineSegments = new ArrayList<>();
            for (int i = 1; i < coordinates.size(); i++) {
                LineSegment ls = new LineSegment(coordinates.get(i-1),coordinates.get(i));
                lineSegments.add(ls);
                featureBuilder.add(ls);
                SimpleFeature feature = featureBuilder.buildFeature(null);
                //features.add(feature);
            }
            Coordinate[] coords = coordinates.toArray(new Coordinate[0]);
            LineString line = geometryFactory.createLineString(coords);
            featureBuilder.add(line);
            SimpleFeature feature = featureBuilder.buildFeature("LineString_Sample");
            features.add(feature);
            File newFile = getFile(new File(filepath));
            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
            Map<String, Serializable> params = new HashMap<>();
            params.put("url", newFile.toURI().toURL());
            params.put("create spatial index", Boolean.TRUE);
            ShapefileDataStore newDataStore =
                    (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
            newDataStore.createSchema(TYPE);
            Transaction transaction = new DefaultTransaction("create");
            String typeName = newDataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
            if (featureSource instanceof SimpleFeatureStore) {
                SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
                SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
                featureStore.setTransaction(transaction);
                try {
                    featureStore.addFeatures(collection);
                    transaction.commit();
                } catch (Exception problem) {
                    problem.printStackTrace();
                    transaction.rollback();
                } finally {
                    transaction.close();
                }
            } else {
                System.out.println(typeName + " does not support read/write access");
                System.exit(1);
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    public static void plotResults(String overlapFilePath) {
        try {
            MapContent map = new MapContent();
            map.setTitle("Test");

            File countries_file = getFile("./src/test/resources/50m_cultural/ne_50m_admin_0_countries_lakes.shp");
            FileDataStore countries_store = FileDataStoreFinder.getDataStore(countries_file);
            SimpleFeatureSource countriesSource = countries_store.getFeatureSource();
            Style country_style = SLD.createPolygonStyle(Color.BLACK,null,1.0f);
            Layer country_layer = new FeatureLayer(countriesSource, country_style);
            map.addLayer(country_layer);

            File groundtrack_file = getFile(overlapFilePath+"groundtrack_terra_16days.shp");
            FileDataStore imag_groundtrack_store = FileDataStoreFinder.getDataStore(groundtrack_file);
            SimpleFeatureSource imag_groundtrackSource = imag_groundtrack_store.getFeatureSource();
            Style imag_style = SLD.createPointStyle("Square",Color.GREEN,Color.GREEN,0.5f,6.0f);
            Style line_style = SLD.createLineStyle(Color.GREEN,6.0f);
            Layer imag_altimeter_track_layer = new FeatureLayer(imag_groundtrackSource, imag_style);
            map.addLayer(imag_altimeter_track_layer);
            JMapFrame.showMap(map);
            saveImage(map, "./src/test/output/groundtrack.jpeg",3000);
        } catch (Exception e) {
            System.out.println("Exception occurred in plotResults: "+e);
        }
    }

    public static Style createGreyscaleStyle(int band) {
        StyleFactory sf = CommonFactoryFinder.getStyleFactory();
        FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
        ContrastEnhancement ce = sf.contrastEnhancement(ff.literal(1.0), ContrastMethod.NORMALIZE);
        SelectedChannelType sct = sf.createSelectedChannelType(String.valueOf(band), ce);

        RasterSymbolizer sym = sf.getDefaultRasterSymbolizer();
        ChannelSelection sel = sf.channelSelection(sct);
        sym.setChannelSelection(sel);

        return SLD.wrapSymbolizers(sym);
    }

    public static Layer generatePointLayer(String filepath, Color color, float opacity, float size) {
        try{
            File file = getFile(filepath);
            FileDataStore fds = FileDataStoreFinder.getDataStore(file);
            SimpleFeatureSource sfSource = fds.getFeatureSource();
            Style style = SLD.createPointStyle("Circle",color,color,opacity,size);
            Layer layer = new FeatureLayer(sfSource,style);
            return layer;
        } catch (Exception e) {
            System.out.println("Exception occurred in generateLayer: "+e);
            return null;
        }
    }
    public static Layer generateHeatLayer(String filepath, Color fillColor, Color outlineColor, float opacity) {
        try{
            File file = getFile(filepath);
            FileDataStore fds = FileDataStoreFinder.getDataStore(file);
            SimpleFeatureSource sfSource = fds.getFeatureSource();
            Style style = SLD.createPolygonStyle(outlineColor,fillColor,opacity);
            Layer layer = new FeatureLayer(sfSource,style);
            return layer;
        } catch (Exception e) {
            System.out.println("Exception occurred in generateLayer: "+e);
            return null;
        }
    }

    public static GroundEventAnalyzer coverageBySatellite(Satellite satellite, double durationDays, Collection<GeodeticPoint> covPoints, AbsoluteDate startDate) {
        long start = System.nanoTime();
        TimeScale utc = TimeScalesFactory.getUTC();
        AbsoluteDate endDate = startDate.shiftedBy(durationDays*86400);
        Frame earthFrame = FramesFactory.getITRF(IERSConventions.IERS_2003, true);
        Frame inertialFrame = FramesFactory.getEME2000();
        BodyShape earthShape = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING, earthFrame);
        PropagatorFactory pf = new PropagatorFactory(PropagatorType.J2);

//        Set<GeodeticPoint> subSet = landPoints.stream()
//                // .skip(10) // Use this to get elements later in the stream
//                .limit(5000)
//                .collect(toCollection(LinkedHashSet::new));
        //create a coverage definition
        CoverageDefinition covDef = new CoverageDefinition("covdef1", covPoints, earthShape);
        //CoverageDefinition covDef = new CoverageDefinition("Whole Earth", granularity, earthShape, UNIFORM);
        HashSet<CoverageDefinition> covDefs = new HashSet<>();
        ArrayList<Satellite> satelliteList = new ArrayList<>();
        satelliteList.add(satellite);
        Constellation constellation = new Constellation("Constellation", satelliteList);
        covDef.assignConstellation(constellation);
        covDefs.add(covDef);

        ArrayList<EventAnalysis> eventAnalyses = new ArrayList<>();
        FieldOfViewEventAnalysis fovea = new FieldOfViewEventAnalysis(startDate, endDate, inertialFrame,covDefs,pf,true, true);
        eventAnalyses.add(fovea);

        Scenario scene = new Scenario.Builder(startDate, endDate, utc).eventAnalysis(eventAnalyses).covDefs(covDefs).name("CoverageBySatellite").propagatorFactory(pf).build();

        try {
            scene.call();
        } catch (Exception e) {
            e.printStackTrace();
        }

        GroundEventAnalyzer gea = new GroundEventAnalyzer(fovea.getEvents(covDef));
        long end = System.nanoTime();
        System.out.printf("coverageBySatellite took %.4f sec\n", (end - start) / Math.pow(10, 9));
        return gea;
    }

    public static HashMap<GndStation, TimeIntervalArray> downlinksBySatellite(Map<Satellite,Set<GndStation>> satelliteMap, double durationDays, AbsoluteDate startDate) {
        long start = System.nanoTime();
        TimeScale utc = TimeScalesFactory.getUTC();
        AbsoluteDate endDate = startDate.shiftedBy(durationDays*86400);
        Frame inertialFrame = FramesFactory.getEME2000();
        PropagatorFactory pf = new PropagatorFactory(PropagatorType.J2);

        ArrayList<EventAnalysis> eventAnalyses = new ArrayList<>();
        GndStationEventAnalysis gsea = new GndStationEventAnalysis(startDate, endDate, inertialFrame,satelliteMap,pf);
        eventAnalyses.add(gsea);

        Scenario scene = new Scenario.Builder(startDate, endDate, utc).eventAnalysis(eventAnalyses).name("GSBySatellite").propagatorFactory(pf).build();

        try {
            scene.call();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Set<Satellite> satSet = satelliteMap.keySet();
        Satellite sat = satSet.iterator().next();
        HashMap<GndStation, TimeIntervalArray> gsMap = gsea.getSatelliteAccesses(sat);
        long end = System.nanoTime();
        System.out.printf("downlinksBySatellite took %.4f sec\n", (end - start) / Math.pow(10, 9));
        return gsMap;
    }

    public static double arraySum(double[] array) {
        double sum = 0;
        for (double value : array) {
            sum += value;
        }
        return sum;
    }
    public static void saveImage(final MapContent map, final String file, final int imageWidth) {

        GTRenderer renderer = new StreamingRenderer();
        renderer.setMapContent(map);

        Rectangle imageBounds = null;
        ReferencedEnvelope mapBounds = null;
        try {
            mapBounds = map.getMaxBounds();
            double heightToWidth = mapBounds.getSpan(1) / mapBounds.getSpan(0);
            imageBounds = new Rectangle(
                    0, 0, imageWidth, (int) Math.round(imageWidth * heightToWidth));

        } catch (Exception e) {
            // failed to access map layers
            throw new RuntimeException(e);
        }

        BufferedImage image = new BufferedImage(imageBounds.width, imageBounds.height, BufferedImage.TYPE_INT_RGB);

        Graphics2D gr = image.createGraphics();
        gr.setPaint(Color.WHITE);
        gr.fill(imageBounds);

        try {
            renderer.paint(gr, imageBounds, mapBounds);
            File fileToSave = new File(file);
            ImageIO.write(image, "jpeg", fileToSave);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static double[] linspace(double min, double max, int points) {
        double[] d = new double[points];
        for (int i = 0; i < points; i++){
            d[i] = min + i * (max - min) / (points - 1);
        }
        return d;
    }
}
