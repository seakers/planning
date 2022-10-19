package seakers.planning;

import org.orekit.bodies.GeodeticPoint;
import seakers.orekit.coverage.access.TimeIntervalArray;

import java.io.*;
import java.util.*;

import static java.lang.Double.parseDouble;

public class NoUpdateSimulator {
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
    public ArrayList<Map<GeodeticPoint,GeophysicalEvent>> globalRewardGridUpdates;
    public ArrayList<Map<GeodeticPoint,GeophysicalEvent>> naiveGlobalRewardGridUpdates;
    public Map<String,ArrayList<GeophysicalEvent>> downlinkedGeophysicalEvents;
    public Map<String,ArrayList<GeophysicalEvent>> naiveDownlinkedGeophysicalEvents;
    public ArrayList<GeophysicalEvent> crosslinkedGeophysicalEvents;
    public Map<String,ArrayList<GeophysicalEvent>> imagedGeophysicalEvents;
    public Map<String, SatelliteState> currentStates;
    public Map<GeodeticPoint,Double> geophysicalLimits;
    public Map<GeodeticPoint,Double> currentGeophysical;
    public double chlReward;
    boolean debug;
    public double endTime;
    private Map<String, Double> results;

    public NoUpdateSimulator(Map<String,String> settings) {
        long start = System.nanoTime();
        filepath = "./src/test/resources/plannerData/oneday_foursats";
        loadCrosslinks();
        loadDownlinks();
        loadObservations();
        loadRewardGrid();
        debug = true;
        chlReward = Double.parseDouble(settings.get("chlBonusReward"));
        endTime = 86400.0;
        results = new HashMap<>();
        localRewardGrids = new HashMap<>();
        actionsTaken = new HashMap<>();
        naiveActionsTaken = new HashMap<>();
        currentPlans = new HashMap<>();
        currentStates = new HashMap<>();
        rewardDownlinked = new HashMap<>();
        naiveRewardDownlinked = new HashMap<>();
        globalRewardGridUpdates = new ArrayList<>();
        naiveGlobalRewardGridUpdates = new ArrayList<>();
        downlinkedGeophysicalEvents = new HashMap<>();
        naiveDownlinkedGeophysicalEvents = new HashMap<>();
        crosslinkedGeophysicalEvents = new ArrayList<>();
        imagedGeophysicalEvents = new HashMap<>();
        ArrayList<String> satList = new ArrayList<>(downlinkEvents.keySet());
        crosslinkInfo = crosslinkInfoInitialize(satList);
        geophysicalLimits = new HashMap<>();
        currentGeophysical = new HashMap<>();
        loadGeophysical();

        // Create initial plans
        for (String sat : satList) {
            localRewardGrids.put(sat,globalRewardGrid);
            actionsTaken.put(sat,new ArrayList<>());
            SatelliteState satelliteState = new SatelliteState(0,0, new ArrayList<>(),70.0,0.0,0.0,0.0, new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
            currentStates.put(sat,satelliteState);
            long planStart = System.nanoTime();
            makePlan(sat,settings);
            long planEnd = System.nanoTime();
            System.out.printf("Took %.4f sec\n", (planEnd - planStart) / Math.pow(10, 9));
            rewardDownlinked.put(sat,0.0);
            imagedGeophysicalEvents.put(sat, new ArrayList<>());
            downlinkedGeophysicalEvents.put(sat, new ArrayList<>());
            System.out.println("Done with initial plan for "+sat);
        }
        double currentTime = 0.0;

        for (String sat : satList) {
            NaivePlanExecutor planExec = new NaivePlanExecutor(currentStates.get(sat),currentTime,86400.0,currentPlans.get(sat), sat, settings);
            updateNaiveGlobalRewardGrid(planExec.getRewardGridUpdates());
            naiveActionsTaken.put(sat,planExec.getActionsTaken());
            naiveRewardDownlinked.put(sat, planExec.getRewardDownlinked());
            naiveDownlinkedGeophysicalEvents.put(sat,planExec.getGeophysicalEvents());
        }

        System.out.println("Done!");
        double naiveReward = 0.0;
        int naiveObservationCount = 0;
        int naiveChargeCount = 0;
        int naiveImagingCount = 0;
        int naiveDownlinkCount = 0;
        Map<GeodeticPoint,Integer> geophysicalObsTracker = new HashMap<>();
        for (String sat : downlinkEvents.keySet()) {
            naiveReward = naiveReward + naiveRewardDownlinked.get(sat);
            naiveObservationCount += naiveDownlinkedGeophysicalEvents.get(sat).size();
            for(GeophysicalEvent chlEvent : downlinkedGeophysicalEvents.get(sat)) {
                if(!geophysicalObsTracker.containsKey(chlEvent.getLocation())) {
                    geophysicalObsTracker.put(chlEvent.getLocation(),1);
                } else {
                    geophysicalObsTracker.put(chlEvent.getLocation(),geophysicalObsTracker.get(chlEvent.getLocation())+1);
                }
            }
            for (SatelliteAction sa : naiveActionsTaken.get(sat)) {
                switch (sa.getActionType()) {
                    case "charge" -> naiveChargeCount++;
                    case "imaging" -> naiveImagingCount++;
                    case "downlink" -> naiveDownlinkCount++;
                }
            }
        }
        results.put("naive",naiveReward);
        results.put("chl count naive",(double)naiveObservationCount);
        Map<GeodeticPoint,Integer> omniscientGeophysicalObsTracker = new HashMap<>();
        ArrayList<GeodeticPoint> gps = new ArrayList<>(globalRewardGrid.keySet());
        int count = 0;
        for (GeodeticPoint gp : gps) {
            if(processImage(gp)) {
                count++;
            }
        }
        System.out.println("Total number of geophysical bloom GPs: "+count);
        for(String sat : satList) {
            ArrayList<Observation> obsList = observationEvents.get(sat);
            for(Observation obs : obsList) {
                if(processImage(obs.getObservationPoint())) {
                    if(!omniscientGeophysicalObsTracker.containsKey(obs.getObservationPoint())) {
                        omniscientGeophysicalObsTracker.put(obs.getObservationPoint(),1);
                    } else {
                        omniscientGeophysicalObsTracker.put(obs.getObservationPoint(),omniscientGeophysicalObsTracker.get(obs.getObservationPoint())+1);
                    }
                }
            }
        }
        if(debug) {
            System.out.println("Naive charge count: "+naiveChargeCount);
            System.out.println("Naive imaging count: "+naiveImagingCount);
            System.out.println("Naive downlink count: "+naiveDownlinkCount);
            System.out.println("Naive reward: "+naiveReward);
            System.out.println("Naive count: "+naiveObservationCount);
        }
        long end = System.nanoTime();
        System.out.printf("Took %.4f sec\n", (end - start) / Math.pow(10, 9));
    }

    public void updateGlobalRewardGrid(Map<GeodeticPoint,GeophysicalEvent> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            globalRewardGrid.put(gp,chlReward);
        }
        globalRewardGridUpdates.add(updates);
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
                    updateGlobalRewardGrid(updates);
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

    public void loadGeophysical() {
        List<List<String>> geophysicalBaselines = new ArrayList<>();
        List<List<String>> geophysicalRecents = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/chlorophyll_baseline.csv"))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                if(count == 0) {
                    count++;
                    continue;
                }
                String[] values = line.split(",");
                geophysicalBaselines.add(Arrays.asList(values));
            }
        } catch (Exception e) {
            System.out.println("Exception occurred in loadCoveragePoints: " + e);
        }
        for (List<String> geophysicalBaseline : geophysicalBaselines) {
            double lon = Math.toRadians(parseDouble(geophysicalBaseline.get(0)));
            double lat = Math.toRadians(parseDouble(geophysicalBaseline.get(1)));
            double mean = parseDouble(geophysicalBaseline.get(2));
            double sd = parseDouble(geophysicalBaseline.get(3));
            GeodeticPoint chloroPoint = new GeodeticPoint(lat, lon, 0.0);
            geophysicalLimits.put(chloroPoint, mean + sd);
        }
        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/chlorophyll_recent.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                geophysicalRecents.add(Arrays.asList(values));
            }
        } catch (Exception e) {
            System.out.println("Exception occurred in loadCoveragePoints: " + e);
        }
        for (int i = 0; i < geophysicalRecents.size(); i++) {
            double lon = Math.toRadians(parseDouble(geophysicalBaselines.get(i).get(0)));
            double lat = Math.toRadians(parseDouble(geophysicalBaselines.get(i).get(1)));
            double chl = parseDouble(geophysicalRecents.get(i).get(2));
            GeodeticPoint chloroPoint = new GeodeticPoint(lat, lon, 0.0);
            currentGeophysical.put(chloroPoint, chl);
        }
    }
    public boolean processImage(GeodeticPoint location) {
        double limit = 0;
        double current = 0;
        for(GeodeticPoint gp : geophysicalLimits.keySet()) {
            if(Math.sqrt(Math.pow(location.getLatitude()-gp.getLatitude(),2)+Math.pow(location.getLongitude()-gp.getLongitude(),2)) < 0.00001) {
                limit = geophysicalLimits.get(gp);
                current = currentGeophysical.get(gp);
                break;
            }
        }
        if(current > limit) {
            return true;
        }
        else {
            return false;
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
            case "ruleBased" -> {
                RuleBasedPlanner ruleBasedPlanner = new RuleBasedPlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                currentPlans.put(sat, ruleBasedPlanner.getResults());
            }
            case "stupidRuleBased" -> {
                StupidRuleBasedPlanner ruleBasedPlanner = new StupidRuleBasedPlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                currentPlans.put(sat, ruleBasedPlanner.getResults());
            }
            case "mcts" -> {
                MCTSPlanner mctsPlanner = new MCTSPlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                currentPlans.put(sat, mctsPlanner.getResults());
            }
            case "dumbMcts" -> {
                DumbMCTSPlanner dumbMctsPlanner = new DumbMCTSPlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                currentPlans.put(sat, dumbMctsPlanner.getResults());
            }
        }
    }

    public Map<String, Double> getResults() { return results; }

}