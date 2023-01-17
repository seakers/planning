package seakers.planning;

import org.orekit.bodies.BodyShape;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.TopocentricFrame;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import seakers.orekit.coverage.access.TimeIntervalArray;
import seakers.orekit.util.OrekitConfig;

import java.io.*;
import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Double.parseDouble;

public class EqualSimulator {
    private String filepath;
    public Map<String, Map<String, TimeIntervalArray>> crosslinkEvents;
    public Map<String, TimeIntervalArray> downlinkEvents;
    public Map<String, ArrayList<Observation>> observationEvents;
    public Map<String, ArrayList<SatelliteAction>> currentPlans;
    public Map<String, ArrayList<SatelliteAction>> actionsTaken;
    public Map<String, ArrayList<SatelliteAction>> naiveActionsTaken;
    public Map<String, Map<GeodeticPoint, Double>> localRewardGrids;
    public Map<String, Double> rewardDownlinked;
    public Map<String, Double> naiveRewardDownlinked;
    public Map<GeodeticPoint,Double> globalRewardGrid;
    public Map<GeodeticPoint,Double> naiveGlobalRewardGrid;
    public Map<GeodeticPoint,Double> centralRewardGrid;
    public Map<String, Map<String,String>> crosslinkInfo;
    public ArrayList<Map<GeodeticPoint,GeophysicalEvent>> naiveGlobalRewardGridUpdates;
    public Map<String,ArrayList<GeophysicalEvent>> naiveDownlinkedGeophysicalEvents;
    public Map<String,ArrayList<GeophysicalEvent>> imagedGeophysicalEvents;
    public Map<String, SatelliteState> currentStates;

    private AbsoluteDate startDate;
    private AbsoluteDate endDate;
    public double chlReward;
    boolean debug;
    public double endTime;
    private Map<String, Double> results;
    public Map<String,Map<GeodeticPoint,ArrayList<TimeIntervalArray>>> gpAccesses;

    public EqualSimulator(Map<String,String> settings, String filepath) {
        long start = System.nanoTime();
        this.filepath = filepath;
        loadCrosslinks();
        loadDownlinks();
        loadObservations();
        loadRewardGrid();
        OrekitConfig.init(1);
        File orekitData = new File("./src/main/resources/orekitResources");
        DataProvidersManager manager = DataProvidersManager.getInstance();
        manager.addProvider(new DirectoryCrawler(orekitData));
        TimeScale utc = TimeScalesFactory.getUTC();
        startDate = new AbsoluteDate(2020, 1, 1, 10, 30, 00.000, utc);
        endDate = startDate.shiftedBy(30.0*86400);
        debug = true;
        endTime = 86400.0*30.0;
        results = new HashMap<>();
        localRewardGrids = new HashMap<>();
        actionsTaken = new HashMap<>();
        naiveActionsTaken = new HashMap<>();
        currentPlans = new HashMap<>();
        currentStates = new HashMap<>();
        rewardDownlinked = new HashMap<>();
        naiveRewardDownlinked = new HashMap<>();
        gpAccesses = new HashMap<>();
        naiveGlobalRewardGridUpdates = new ArrayList<>();
        ArrayList<String> satList = new ArrayList<>(downlinkEvents.keySet());
        crosslinkInfo = crosslinkInfoInitialize(satList);
        for (String sat : downlinkEvents.keySet()) {
            Map<GeodeticPoint,ArrayList<TimeIntervalArray>> gpAccessesPerSat = new HashMap<>();
            for (GeodeticPoint gp : globalRewardGrid.keySet()) {
                ArrayList<TimeIntervalArray> tias = new ArrayList<>();
                gpAccessesPerSat.put(gp,tias);
            }
            gpAccesses.put(sat,gpAccessesPerSat);
        }
        // Create initial plans
        for (String sat : satList) {
            localRewardGrids.put(sat,globalRewardGrid);
            actionsTaken.put(sat,new ArrayList<>());
            SatelliteState satelliteState = new SatelliteState(0,0, new ArrayList<>(),70.0,0.0,0.0,0.0, new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
            currentStates.put(sat,satelliteState);
            long planStart = System.nanoTime();
            makePlan(sat,settings);
            //System.out.println("Plan for "+sat+": "+currentPlans.get(sat));
            long planEnd = System.nanoTime();
            System.out.printf("Took %.4f sec\n", (planEnd - planStart) / Math.pow(10, 9));
            rewardDownlinked.put(sat,0.0);
            System.out.println("Done with initial plan for "+sat);
        }
        double currentTime = 0.0;

        for (String sat : satList) {
            NaivePlanExecutor planExec = new NaivePlanExecutor(currentStates.get(sat),currentTime,endTime,currentPlans.get(sat), sat, settings);
            updateNaiveGlobalRewardGrid(planExec.getRewardGridUpdates());
            naiveActionsTaken.put(sat,planExec.getActionsTaken());
        }
        System.out.println("Done!");
        computeStatistics("Non-reactive",naiveActionsTaken);
        long end = System.nanoTime();
        System.out.printf("Took %.4f sec\n", (end - start) / Math.pow(10, 9));
    }

    public void computeStatistics(String flag, Map<String, ArrayList<SatelliteAction>> takenActions) {
        int chargeCount = 0;
        int imagingCount = 0;
        int downlinkCount = 0;
        for (String sat : downlinkEvents.keySet()) {
            Map<GeodeticPoint,ArrayList<TimeIntervalArray>> gpAccessesPerSat = gpAccesses.get(sat);
            for (SatelliteAction sa : takenActions.get(sat)) {
                switch (sa.getActionType()) {
                    case "charge":
                        chargeCount++;
                        break;
                    case "imaging":
                        GeodeticPoint gp = sa.getLocation();
                        ArrayList<GeodeticPoint> nearbyGPs = getPointsInFOV(gp,new ArrayList<>(globalRewardGrid.keySet()));
                        for (GeodeticPoint nearbyGP : nearbyGPs) {
                            TimeIntervalArray tia = new TimeIntervalArray(startDate,endDate);
                            tia.addRiseTime(sa.gettStart());
                            tia.addSetTime(sa.gettEnd());
                            ArrayList<TimeIntervalArray> tias = gpAccessesPerSat.get(nearbyGP);
                            tias.add(tia);
                            gpAccessesPerSat.put(nearbyGP,tias);
                        }
                        imagingCount++;
                        break;
                    case "downlink":
                        downlinkCount++;
                        break;
                }
            }
            gpAccesses.put(sat,gpAccessesPerSat);
        }
        //System.out.println(flag+" charge count: "+chargeCount);
        //System.out.println(flag+" imaging count: "+imagingCount);
        //System.out.println(flag+" downlink count: "+downlinkCount);
    }
    public void updateNaiveGlobalRewardGrid(Map<GeodeticPoint,GeophysicalEvent> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            naiveGlobalRewardGrid.put(gp,chlReward);
        }
        naiveGlobalRewardGridUpdates.add(updates);
    }
    public void updateCentralRewardGrid(Map<GeodeticPoint,GeophysicalEvent> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            centralRewardGrid.put(gp,chlReward);
        }
    }
    public void updateLocalRewardGrid(String sat, Map<GeodeticPoint,GeophysicalEvent> updates) {
        Map<GeodeticPoint,Double> oldRewardGrid = localRewardGrids.get(sat);
        for(GeodeticPoint gp : updates.keySet()) {
            oldRewardGrid.put(gp,chlReward);
        }
        localRewardGrids.put(sat,oldRewardGrid);
    }

    public void checkCrosslinks(String sat, double currentTime, double endTime, Map<GeodeticPoint,GeophysicalEvent> updates) {
        Map<String, TimeIntervalArray> crosslinksBySat = crosslinkEvents.get(sat);
        for(String clSat : crosslinksBySat.keySet()) {
            TimeIntervalArray clArray = crosslinksBySat.get(clSat);
            for (int i = 0; i < clArray.getRiseAndSetTimesList().length; i = i + 2) {
                if (clArray.getRiseAndSetTimesList()[i] < currentTime && clArray.getRiseAndSetTimesList()[i + 1] > currentTime && !updates.isEmpty()) {
                    updateLocalRewardGrid(clSat, updates);
                    updateLocalRewardGrid(sat, updates);
                }
            }
        }
    }

    public void loadCrosslinks() {
        File directory = new File(filepath);
        Map<String, Map<String, TimeIntervalArray>> cl = new HashMap<>();
        Map<String, TimeIntervalArray> loadedIntervals;
        try {
            String[] directories = directory.list((current, name) -> new File(current, name).isDirectory());
            for(int i = 0; i < Objects.requireNonNull(directory.list()).length-1; i++) {
                FileInputStream fi = new FileInputStream(filepath+"/"+directories[i]+"/crosslinks.dat");
                ObjectInputStream oi = new ObjectInputStream(fi);
                loadedIntervals = (Map<String, TimeIntervalArray>) oi.readObject();
                cl.put(directories[i],loadedIntervals);
                oi.close();
                fi.close();
            }
            crosslinkEvents = cl;
        } catch (Exception e) {
            System.out.println("Exception in loadCrosslinks: "+e);
        }
    }

    public void loadDownlinks() {
        File directory = new File(filepath);
        Map<String, TimeIntervalArray> dl = new HashMap<>();
        try{
            String[] directories = directory.list((current, name) -> new File(current, name).isDirectory());
            for(int i = 0; i < directory.list().length-1; i++) {
                FileInputStream fi = new FileInputStream(filepath+"/"+directories[i]+"/downlinks.dat");
                ObjectInputStream oi = new ObjectInputStream(fi);

                TimeIntervalArray downlinkIntervals =  (TimeIntervalArray) oi.readObject();

                dl.put(directories[i],downlinkIntervals);

                oi.close();
                fi.close();
            }
        } catch (Exception e) {
            System.out.println("Exception in loadDownlinks: "+e.getMessage());
        }
        downlinkEvents = dl;

    }

    public void loadObservations() {
        File directory = new File(filepath);
        Map<String, ArrayList<Observation>> obs = new HashMap<>();
        try{
            String[] directories = directory.list((current, name) -> new File(current, name).isDirectory());
            for(int i = 0; i < directory.list().length-1; i++) {
                FileInputStream fi = new FileInputStream(filepath+"/"+directories[i]+"/observations.dat");
                ObjectInputStream oi = new ObjectInputStream(fi);

                ArrayList<Observation> observations =  (ArrayList<Observation>) oi.readObject();
                obs.put(directories[i],observations);

                oi.close();
                fi.close();
            }
        } catch (Exception e) {
            System.out.println("Exception in loadObservations: "+e.getMessage());
        }
        observationEvents = obs;
    }

    public void loadRewardGrid() {
        File directory = new File(filepath);
        try{
            for(int i = 0; i < directory.list().length-1; i++) {
                FileInputStream fi = new FileInputStream(filepath+"/coveragePoints.dat");
                ObjectInputStream oi = new ObjectInputStream(fi);

                globalRewardGrid =  (Map<GeodeticPoint,Double>) oi.readObject();
                globalRewardGrid.replaceAll((g, v) -> 1.0);
                naiveGlobalRewardGrid = new HashMap<>(globalRewardGrid);
                centralRewardGrid = new HashMap<>(globalRewardGrid);

                oi.close();
                fi.close();
            }
        } catch (Exception e) {
            System.out.println("Exception in loadRewardGrid: "+e.getMessage());
        }
    }

    public Map<String,Map<String,String>> crosslinkInfoInitialize(ArrayList<String> satList) {
        Map<String,Map<String,String>> crosslinkInfo = new HashMap<>();
        for (String sat : satList) {
            Map<String,String> crosslinkFlags = new HashMap<>();
            for (String otherSat : satList) {
                crosslinkFlags.put(otherSat,"no new info");
            }
            crosslinkInfo.put(sat,crosslinkFlags);
        }
        return crosslinkInfo;
    }

    public void makePlan(String sat, Map<String,String> settings) {
        switch (settings.get("planner")) {
            case "ruleBased":
                RuleBasedPlanner ruleBasedPlanner = new RuleBasedPlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                currentPlans.put(sat, ruleBasedPlanner.getResults());
                break;
            case "ruleBased_coverage":
                RuleBasedCoveragePlanner ruleBasedCoveragePlanner = new RuleBasedCoveragePlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                currentPlans.put(sat, ruleBasedCoveragePlanner.getResults());
                break;
            case "mcts":
                MCTSPlanner mctsPlanner = new MCTSPlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                currentPlans.put(sat, mctsPlanner.getResults());
                break;
            case "dumbMcts":
                DumbMCTSPlanner dumbMctsPlanner = new DumbMCTSPlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                currentPlans.put(sat, dumbMctsPlanner.getResults());
                break;
        }
    }

    public Map<String, Double> getResults() { return results; }

    public Map<String,Map<GeodeticPoint,ArrayList<TimeIntervalArray>>> getPlannerAccesses() {
        return gpAccesses;
    }
    ArrayList<GeodeticPoint> getPointsInFOV(GeodeticPoint location, ArrayList<GeodeticPoint> groundPoints) {
        ArrayList<GeodeticPoint> pointsInFOV = new ArrayList<>();
        for (GeodeticPoint gp : groundPoints) {
            double distance = Math.sqrt(Math.pow(location.getLatitude()-gp.getLatitude(),2)+Math.pow(location.getLongitude()-gp.getLongitude(),2)); // in radians latitude
            double radius = 577; // kilometers for 500 km orbit height, 30 deg half angle, NOT spherical trig TODO
            if(distance * 111.1 * 180 / Math.PI < radius) {
                pointsInFOV.add(gp);
            }
        }
        return pointsInFOV;
    }
}
