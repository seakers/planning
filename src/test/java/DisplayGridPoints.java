import org.geotools.data.*;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.FilterFactory2;
import org.opengis.style.ContrastMethod;
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
import seakers.orekit.analysis.Analysis;
import seakers.orekit.analysis.Record;
import seakers.orekit.analysis.ephemeris.GroundTrackAnalysis;
import seakers.orekit.coverage.access.TimeIntervalArray;
import seakers.orekit.coverage.access.TimeIntervalMerger;
import seakers.orekit.coverage.analysis.GroundEventAnalyzer;
import seakers.orekit.event.EventAnalysis;
import seakers.orekit.event.FieldOfViewEventAnalysis;
import seakers.orekit.event.GndStationEventAnalysis;
import seakers.orekit.object.*;
import seakers.orekit.object.communications.ReceiverAntenna;
import seakers.orekit.object.communications.TransmitterAntenna;
import seakers.orekit.object.fieldofview.NadirRectangularFOV;
import seakers.orekit.propagation.PropagatorFactory;
import seakers.orekit.propagation.PropagatorType;
import seakers.orekit.scenario.Scenario;
import seakers.orekit.util.OrekitConfig;
import seakers.planning.Observation;
import seakers.planning.SMDPPlanner;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Double.parseDouble;
import static org.apache.commons.io.FileUtils.getFile;
import static seakers.orekit.object.CommunicationBand.S;

public class DisplayGridPoints {

    public static void main(String[] args) {

        // Orekit initialization needs
        OrekitConfig.init(4);
        String greedyPlannerFilePath = "./src/test/gridPointOutput/";

//        ArrayList<GeodeticPoint> allGPs = new ArrayList<>();
//        List<List<String>> records = new ArrayList<>();
//        List<List<String>> lats = new ArrayList<>();
//        List<List<String>> lons = new ArrayList<>();
//        ArrayList<GeodeticPoint> reducedGPs = new ArrayList<>();
//        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/IGBP.csv"))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                String[] values = line.split(",");
//                records.add(Arrays.asList(values));
//            }
//        }
//        catch (Exception e) {
//            System.out.println(e);
//        }
//        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/IGBP_lats.csv"))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                String[] values = line.split(",");
//                lats.add(Arrays.asList(values));
//            }
//        }
//        catch (Exception e) {
//            System.out.println(e);
//        }
//        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/IGBP_lons.csv"))) {
//            String line;
//            while ((line = br.readLine()) != null) {
//                String[] values = line.split(",");
//                lons.add(Arrays.asList(values));
//            }
//        }
//        catch (Exception e) {
//            System.out.println(e);
//        }
//        ArrayList<GeodeticPoint> igbpPoints = new ArrayList<>();
//        double longDistCheck = 180.0;
//        double latDistCheck = 180.0;
//        for (int j = 0; j < records.get(0).size(); j++) {
//            for (int k = 0; k < records.size(); k++) {
//                // Check for IGBP biome types
//                // Change doubles in this if statement to change grid granularity
//                if ((records.get(k).get(j).equals("1") || records.get(k).get(j).equals("2") || records.get(k).get(j).equals("3") || records.get(k).get(j).equals("4") || records.get(k).get(j).equals("5") || records.get(k).get(j).equals("8") || records.get(k).get(j).equals("9"))) {
//                    allGPs.add(new GeodeticPoint(Math.toRadians(Double.parseDouble(lats.get(k).get(j))), Math.toRadians(Double.parseDouble(lons.get(k).get(j))), 0.0));
//                    for(GeodeticPoint gp : igbpPoints) {
//                        if(Math.sqrt((gp.getLatitude()-)))
//                    }
//                }
//                for(igbpP)
//                if (latDistCheck > 1.0 && longDistCheck > 1.0 && (records.get(k).get(j).equals("1") || records.get(k).get(j).equals("2") || records.get(k).get(j).equals("3") || records.get(k).get(j).equals("4") || records.get(k).get(j).equals("5") || records.get(k).get(j).equals("8") || records.get(k).get(j).equals("9"))) {
//                    GeodeticPoint point = new GeodeticPoint(Math.toRadians(Double.parseDouble(lats.get(k).get(j))), Math.toRadians(Double.parseDouble(lons.get(k).get(j))), 0.0);
//                    igbpPoints.add(point);
//                    latDistCheck = 0.0;
//                    longDistCheck = 0.0;
//                }
//                latDistCheck = latDistCheck+180.0/records.size();
//            }
//            latDistCheck = 0.0;
//            longDistCheck = longDistCheck+360.0/records.get(0).size();
//        }
//        reducedGPs = igbpPoints;
        //processGPs(reducedGPs,greedyPlannerFilePath+"reduced.shp");
        //processGPs(allGPs,greedyPlannerFilePath+"all.shp");

        plotResults(greedyPlannerFilePath);
    }

    public static double[] LLAtoECI(GeodeticPoint point) {
        double re = 6370;
        double x = re * Math.cos(point.getLatitude()) * Math.cos(point.getLongitude());
        double y = re * Math.cos(point.getLatitude()) * Math.sin(point.getLongitude());
        double z = re * Math.sin(point.getLatitude());
        double[] result = {x,y,z};
        return result;
    }
    public static void processGPs(ArrayList<GeodeticPoint> gps, String filepath) {
        try {

            final SimpleFeatureType TYPE =
                    DataUtilities.createType(
                            "Location",
                            "the_geom:Point:srid=4326,"
                    );
            List<SimpleFeature> features = new ArrayList<>();
            GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
            for (GeodeticPoint gp : gps) {
                Point point = geometryFactory.createPoint(new Coordinate(FastMath.toDegrees(gp.getLatitude()), FastMath.toDegrees(gp.getLongitude())));
                featureBuilder.add(point);
                SimpleFeature feature = featureBuilder.buildFeature(null);
                features.add(feature);
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
            e.printStackTrace();
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

            //map.addLayer(generatePointLayer(overlapFilePath+"all.shp",Color.RED,0.5f,12.0f));
            map.addLayer(generatePointLayer(overlapFilePath+"reduced.shp",Color.BLUE,0.5f,12.0f));

            JMapFrame.showMap(map);
            saveImage(map, "./src/test/output/gridpoints.jpeg",3000);
        } catch (Exception e) {
            System.out.println("Exception occurred in plotResults: "+e);
        }
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
