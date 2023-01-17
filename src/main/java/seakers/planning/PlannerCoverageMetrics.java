package seakers.planning;

import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.hipparchus.util.FastMath;
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
import seakers.orekit.coverage.analysis.AnalysisMetric;
import seakers.orekit.event.*;
import seakers.orekit.object.communications.NearEarthNetwork;
import seakers.orekit.analysis.Analysis;
import seakers.orekit.analysis.Record;
import seakers.orekit.analysis.ephemeris.GroundTrackAnalysis;
import seakers.orekit.coverage.access.TimeIntervalArray;
import seakers.orekit.coverage.access.TimeIntervalMerger;
import seakers.orekit.coverage.analysis.GroundEventAnalyzer;
import seakers.orekit.object.*;
import seakers.orekit.object.fieldofview.NadirRectangularFOV;
import seakers.orekit.propagation.PropagatorFactory;
import seakers.orekit.propagation.PropagatorType;
import seakers.orekit.scenario.Scenario;
import seakers.orekit.util.OrekitConfig;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Double.parseDouble;

@SuppressWarnings({"unchecked"})

public class PlannerCoverageMetrics {

    public String plannerRepoFilePath;
    public double durationDays;
    public AbsoluteDate startDate;

    public Map<String, Map<TopocentricFrame,TimeIntervalArray>> accessEvents;

    private Properties propertiesPropagator;

    public PlannerCoverageMetrics(String plannerRepo, Map<String,Map<GeodeticPoint,ArrayList<TimeIntervalArray>>> plannerAccesses) {
        plannerRepoFilePath = plannerRepo;
        durationDays = 30.0;
        propertiesPropagator = new Properties();
        OrekitConfig.init(4);
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
        startDate = new AbsoluteDate(2020, 1, 1, 10, 30, 00.000, utc);
        AbsoluteDate endDate = startDate.shiftedBy(durationDays*86400);
        double mu = Constants.WGS84_EARTH_MU;
        Map<GeodeticPoint,Double> covPoints = loadCoveragePoints();
        ArrayList<TopocentricFrame> tfPoints = new ArrayList<>();
        for (GeodeticPoint gp : covPoints.keySet()) {
            tfPoints.add(new TopocentricFrame(earthShape,gp,""));
        }
        Map<TopocentricFrame,TimeIntervalArray> fovEventsPlanned = new HashMap<>();
        Map<TopocentricFrame,TimeIntervalArray> fovEventsAll = new HashMap<>();
        Map<TopocentricFrame,TimeIntervalArray> forEvents = new HashMap<>();
        loadAccesses();
        for (String sat : plannerAccesses.keySet()){
            Map<GeodeticPoint,ArrayList<TimeIntervalArray>> plannerAccessesPerSat = plannerAccesses.get(sat);
            for (GeodeticPoint gp : plannerAccessesPerSat.keySet()) {
                TopocentricFrame tf = new TopocentricFrame(earthShape,gp,"");
                ArrayList<TimeIntervalArray> tias = plannerAccessesPerSat.get(gp);
                TimeIntervalArray baseTIA = new TimeIntervalArray(startDate,endDate);
                tias.add(baseTIA);
                TimeIntervalMerger merger = new TimeIntervalMerger(tias);
                TimeIntervalArray combinedArray = merger.orCombine();
                fovEventsPlanned.put(tf,combinedArray);
            }
        }
        forEvents = accessEvents.get("smallsat00");
        for(String sat : accessEvents.keySet()) {
            Map<TopocentricFrame, TimeIntervalArray> forAccessesPerSat = accessEvents.get(sat);
            for (TopocentricFrame tf : forAccessesPerSat.keySet()) {
                for(TopocentricFrame tf2 : forEvents.keySet()) {
                    if(tf.getPoint().getLatitude() == tf2.getPoint().getLatitude() && tf.getPoint().getLongitude() == tf2.getPoint().getLongitude()) {
                        TimeIntervalArray tia = forEvents.get(tf);
                        TimeIntervalArray tia2 = forAccessesPerSat.get(tf2);
                        ArrayList<TimeIntervalArray> tias = new ArrayList<>();
                        tias.add(tia);
                        tias.add(tia2);
                        TimeIntervalMerger merger = new TimeIntervalMerger(tias);
                        TimeIntervalArray combinedArray = merger.orCombine();
                        forEvents.put(tf,combinedArray);
                    }
                }
            }
        }
        double[] latBounds = new double[]{FastMath.toRadians(-75), FastMath.toRadians(75)};
        double[] lonBounds = new double[]{FastMath.toRadians(-180), FastMath.toRadians(180)};
        double fovAvgRevisitPlanned = getAverageRevisitTime(fovEventsPlanned,latBounds,lonBounds)/3600;
        double fovMedRevisitPlanned = getMedianRevisitTime(fovEventsPlanned,latBounds,lonBounds)/3600;
        double[] fovAvgRevisitsPlanned = getRevisits(fovEventsPlanned,latBounds,lonBounds);
        for (int i = 0; i < fovAvgRevisitsPlanned.length; i++) {
            fovAvgRevisitsPlanned[i] = fovAvgRevisitsPlanned[i]/3600;
        }
        //System.out.println("FOV avg revisits, planned points: "+ Arrays.toString(fovAvgRevisitsPlanned));
        double fovMaxRevisitPlanned = getMaxRevisitTime(fovEventsPlanned,latBounds,lonBounds)/3600;
        double fovPercentCoveragePlanned = getPercentCoverage(fovEventsPlanned,latBounds,lonBounds);
        System.out.printf("FOV avg revisit time: %.2f\n",fovAvgRevisitPlanned);
        System.out.printf("FOV med revisit time: %.2f\n",fovMedRevisitPlanned);
        System.out.printf("FOV max revisit time: %.2f\n",fovMaxRevisitPlanned);
        System.out.printf("FOV percent coverage: %.2f\n",fovPercentCoveragePlanned);
        double forAvgRevisit = getAverageRevisitTime(forEvents,latBounds,lonBounds)/3600;
        double forMedRevisit = getMedianRevisitTime(forEvents,latBounds,lonBounds)/3600;
        double forMaxRevisit = getMaxRevisitTime(forEvents,latBounds,lonBounds)/3600;
        double forPercentCoverage = getPercentCoverage(forEvents,latBounds,lonBounds);
        System.out.printf("FOR avg revisit time, all points: %.2f\n",forAvgRevisit);
        System.out.printf("FOR med revisit time, all points: %.2f\n",forMedRevisit);
        System.out.printf("FOR max revisit time, all points: %.2f\n",forMaxRevisit);
        System.out.printf("FOR percent coverage, all points: %.2f\n",forPercentCoverage);
        OrekitConfig.end();
    }
    public double getAverageRevisitTime(Map<TopocentricFrame, TimeIntervalArray> accesses, double[] latBounds, double[] lonBounds){
        // Method to compute average revisit time from accesses

        GroundEventAnalyzer eventAnalyzer = new GroundEventAnalyzer(accesses);

        DescriptiveStatistics stat;

        if(latBounds.length == 0 && lonBounds.length == 0){
            stat = eventAnalyzer.getStatistics(AnalysisMetric.DURATION, false, this.propertiesPropagator);

        } else {
            stat = eventAnalyzer.getStatistics(AnalysisMetric.DURATION, false, latBounds, lonBounds, this.propertiesPropagator);
        }
        return stat.getMean();
    }

    public double getMedianRevisitTime(Map<TopocentricFrame, TimeIntervalArray> accesses, double[] latBounds, double[] lonBounds){
        // Method to compute average revisit time from accesses

        GroundEventAnalyzer eventAnalyzer = new GroundEventAnalyzer(accesses);

        DescriptiveStatistics stat;

        if(latBounds.length == 0 && lonBounds.length == 0){
            stat = eventAnalyzer.getStatistics(AnalysisMetric.DURATION, false, this.propertiesPropagator);

        } else {
            stat = eventAnalyzer.getStatistics(AnalysisMetric.DURATION, false, latBounds, lonBounds, this.propertiesPropagator);
        }
        return stat.getPercentile(50);
    }

    public double[] getRevisits(Map<TopocentricFrame, TimeIntervalArray> accesses, double[] latBounds, double[] lonBounds){
        // Method to compute average revisit time from accesses

        GroundEventAnalyzer eventAnalyzer = new GroundEventAnalyzer(accesses);

        DescriptiveStatistics stat;

        if(latBounds.length == 0 && lonBounds.length == 0){
            stat = eventAnalyzer.getStatistics(AnalysisMetric.DURATION, false, this.propertiesPropagator);

        } else {
            stat = eventAnalyzer.getStatistics(AnalysisMetric.DURATION, false, latBounds, lonBounds, this.propertiesPropagator);
        }
        return stat.getSortedValues();
    }

    public double getMaxRevisitTime(Map<TopocentricFrame, TimeIntervalArray> accesses, double[] latBounds, double[] lonBounds){
        // Method to compute average revisit time from accesses

        GroundEventAnalyzer eventAnalyzer = new GroundEventAnalyzer(accesses);

        DescriptiveStatistics stat;

        if(latBounds.length == 0 && lonBounds.length == 0){
            stat = eventAnalyzer.getStatistics(AnalysisMetric.DURATION, false, this.propertiesPropagator);

        } else {
            stat = eventAnalyzer.getStatistics(AnalysisMetric.DURATION, false, latBounds, lonBounds, this.propertiesPropagator);
        }
        return stat.getPercentile(95);
    }

    public double getPercentCoverage(Map<TopocentricFrame, TimeIntervalArray> accesses, double[] latBounds, double[] lonBounds){
        // Method to compute average revisit time from accesses

        GroundEventAnalyzer eventAnalyzer = new GroundEventAnalyzer(accesses);

        DescriptiveStatistics stat;

        if(latBounds.length == 0 && lonBounds.length == 0){
            stat = eventAnalyzer.getStatistics(AnalysisMetric.PERCENT_COVERAGE, true, this.propertiesPropagator);

        } else {
            stat = eventAnalyzer.getStatistics(AnalysisMetric.PERCENT_COVERAGE, true, latBounds, lonBounds, this.propertiesPropagator);
        }

        return stat.getMean();
    }

    public Map<GeodeticPoint,Double> loadCoveragePoints() {
        Map<GeodeticPoint, Double> pointRewards = new HashMap<>();
        if (!new File(plannerRepoFilePath + "/coveragePoints.dat").exists()) {
            // Loading river and lake constant scores
            List<List<String>> riverRecords = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/grwl_river_output.csv"))) { // CHANGE THIS FOR YOUR IMPLEMENTATION
                String line;
                while ((line = br.readLine()) != null) {
                    String[] values = line.split(",");
                    riverRecords.add(Arrays.asList(values));
                }
            } catch (Exception e) {
                System.out.println("Exception occurred in loadCoveragePoints: " + e);
            }
            for (int i = 0; i < 1000; i++) {
                double lon = Math.toRadians(parseDouble(riverRecords.get(i).get(0)));
                double lat = Math.toRadians(parseDouble(riverRecords.get(i).get(1)));
                double width = parseDouble(riverRecords.get(i).get(2));
                GeodeticPoint riverPoint = new GeodeticPoint(lat, lon, 0.0);
                pointRewards.put(riverPoint, width / 5000.0 / 2);
            }
            List<List<String>> lakeRecords = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/hydrolakes.csv"))) { // CHANGE THIS FOR YOUR IMPLEMENTATION
                String line;
                while ((line = br.readLine()) != null) {
                    String[] values = line.split(",");
                    lakeRecords.add(Arrays.asList(values));
                }
            } catch (Exception e) {
                System.out.println("Exception occurred in loadCoveragePoints: " + e);
            }
            for (int i = 1; i < 1000; i++) {
                double lat = Math.toRadians(parseDouble(lakeRecords.get(i).get(0)));
                double lon = Math.toRadians(parseDouble(lakeRecords.get(i).get(1)));
                double area = parseDouble(lakeRecords.get(i).get(2));
                GeodeticPoint lakePoint = new GeodeticPoint(lat, lon, 0.0);
                pointRewards.put(lakePoint, area / 30000.0);
            }
            try {
                File file = new File(plannerRepoFilePath+"/coveragePoints.dat");
                FileOutputStream fos=new FileOutputStream(file);
                ObjectOutputStream oos=new ObjectOutputStream(fos);

                oos.writeObject(pointRewards);
                oos.flush();
                oos.close();
                fos.close();
            } catch (Exception e) {
                System.out.println("Exception in loadCoveragePoints: "+e);
            }

        } else {
            try {
                File toRead=new File(plannerRepoFilePath+"/coveragePoints.dat");
                FileInputStream fis=new FileInputStream(toRead);
                ObjectInputStream ois=new ObjectInputStream(fis);

                pointRewards=(Map<GeodeticPoint,Double>)ois.readObject();

                ois.close();
                fis.close();
            } catch(Exception e) {
                System.out.println("Exception in loadCoveragePoints: "+e);
            }
        }

        return pointRewards;
    }
    public void loadAccesses() {
        File directory = new File(plannerRepoFilePath);
        Map<String, Map<TopocentricFrame,TimeIntervalArray>> acc = new HashMap<>();
        try{
            String[] directories = directory.list((current, name) -> new File(current, name).isDirectory());
            for(int i = 0; i < directory.list().length-1; i++) {
                FileInputStream fi = new FileInputStream(plannerRepoFilePath+"/"+directories[i]+"/accesses.dat");
                ObjectInputStream oi = new ObjectInputStream(fi);

                Map<TopocentricFrame,TimeIntervalArray> accesses =  (Map<TopocentricFrame,TimeIntervalArray>) oi.readObject();
                acc.put(directories[i],accesses);

                oi.close();
                fi.close();
            }
        } catch (Exception e) {
            System.out.println("Exception in loadObservations: "+e.getMessage());
        }
        accessEvents = acc;
    }

}
