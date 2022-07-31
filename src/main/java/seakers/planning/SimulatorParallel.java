package seakers.planning;

import org.orekit.bodies.GeodeticPoint;
import seakers.orekit.coverage.access.TimeIntervalArray;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

import static java.lang.Double.parseDouble;

public class SimulatorParallel implements Callable<Map<String,Double>> {
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
    public ArrayList<Map<GeodeticPoint,ChlorophyllEvent>> globalRewardGridUpdates;
    public ArrayList<Map<GeodeticPoint,ChlorophyllEvent>> naiveGlobalRewardGridUpdates;
    public Map<String,ArrayList<ChlorophyllEvent>> downlinkedChlorophyllEvents;
    public Map<String,ArrayList<ChlorophyllEvent>> naiveDownlinkedChlorophyllEvents;
    public ArrayList<ChlorophyllEvent> crosslinkedChlorophyllEvents;
    public Map<String,ArrayList<ChlorophyllEvent>> imagedChlorophyllEvents;
    public Map<String, SatelliteState> currentStates;
    public Map<GeodeticPoint,Double> chlorophyllLimits;
    public Map<GeodeticPoint,Double> currentChlorophyll;
    public double chlReward;
    boolean debug;
    public double endTime;
    private Map<String, Double> results;

    private Map<String, String> settings;

    public SimulatorParallel(Map<String,String> settings) {
        this.settings = settings;
    }

    public Map<String,Double> call() throws Exception {
        System.out.println("starting simulator");
        long start = System.nanoTime();
        filepath = "./src/test/resources/plannerData/oneday";
        loadCrosslinks();
        loadDownlinks();
        loadObservations();
        loadRewardGrid();
        debug = true;
        chlReward = 1.0 + Double.parseDouble(settings.get("chlBonusReward"));
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
        downlinkedChlorophyllEvents = new HashMap<>();
        naiveDownlinkedChlorophyllEvents = new HashMap<>();
        crosslinkedChlorophyllEvents = new ArrayList<>();
        imagedChlorophyllEvents = new HashMap<>();
        ArrayList<String> satList = new ArrayList<>(downlinkEvents.keySet());
        crosslinkInfo = crosslinkInfoInitialize(satList);
        chlorophyllLimits = new HashMap<>();
        currentChlorophyll = new HashMap<>();
        loadChlorophyll();

        // Create initial plans
        for (String sat : satList) {
            localRewardGrids.put(sat,globalRewardGrid);
            actionsTaken.put(sat,new ArrayList<>());
            SatelliteState satelliteState = new SatelliteState(0,0, new ArrayList<>(),70.0,0.0,0.0,0.0, new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
            currentStates.put(sat,satelliteState);
            makePlan(sat,settings);
            rewardDownlinked.put(sat,0.0);
            imagedChlorophyllEvents.put(sat, new ArrayList<>());
            downlinkedChlorophyllEvents.put(sat, new ArrayList<>());

        }
        System.out.println("Done with initial plans");
        double currentTime = 0.0;

        for (String sat : satList) {
            NaivePlanExecutor planExec = new NaivePlanExecutor(currentStates.get(sat),currentTime,86400.0,currentPlans.get(sat), sat, settings);
            updateNaiveGlobalRewardGrid(planExec.getRewardGridUpdates());
            naiveActionsTaken.put(sat,planExec.getActionsTaken());
            naiveRewardDownlinked.put(sat, planExec.getRewardDownlinked());
            naiveDownlinkedChlorophyllEvents.put(sat,planExec.getChlorophyllEvents());
        }

        String planFlag;
        while (currentTime < endTime) {
            //System.out.println("Currently at: "+currentTime);
            double earliestStopTime = endTime;
            // determine earliest stop time based on replanning flags
            for (String sat : satList) {
                PlanExecutor planExec = new PlanExecutor(currentStates.get(sat),currentTime,earliestStopTime,currentPlans.get(sat), sat, settings);
                double planTerminationTime = planExec.getStopTime();
                if(planTerminationTime < earliestStopTime) {
                    earliestStopTime = planTerminationTime;
                }
            }
            for (String sat : satList) {
                PlanExecutor planExec = new PlanExecutor(currentStates.get(sat), currentTime, earliestStopTime, currentPlans.get(sat), sat, settings);
                ArrayList<SatelliteAction> actionsSoFar = actionsTaken.get(sat);
                actionsSoFar.addAll(planExec.getActionsTaken());
                actionsTaken.put(sat, actionsSoFar);
                currentStates.put(sat, planExec.getReturnState());
                planFlag = planExec.getReplanFlag();
                switch (planFlag) {
                    case "":
                        break;
                    case "downlink": {
                        updateCentralRewardGrid(planExec.getRewardGridUpdates());
                        updateGlobalRewardGrid(planExec.getRewardGridUpdates());
                        rewardDownlinked.put(sat, rewardDownlinked.get(sat) + planExec.getRewardDownlinked());
                        ArrayList<ChlorophyllEvent> tempList = new ArrayList<>(downlinkedChlorophyllEvents.get(sat));
                        tempList.addAll(imagedChlorophyllEvents.get(sat));
                        downlinkedChlorophyllEvents.put(sat, tempList);
                        imagedChlorophyllEvents.put(sat, new ArrayList<>());
                        localRewardGrids.put(sat, centralRewardGrid);
                        makePlan(sat,settings);
                        break;
                    }
                    case "image": {
                        updateLocalRewardGrid(sat, planExec.getRewardGridUpdates());
                        updateGlobalRewardGrid(planExec.getRewardGridUpdates());
                        imagedChlorophyllEvents.get(sat).addAll(planExec.getChlorophyllEvents());
                        for (String otherSat : crosslinkInfo.get(sat).keySet()) {
                            crosslinkInfo.get(sat).put(otherSat, "new info");
                        }
                        makePlan(sat,settings);
                        break;
                    }
                    default: {
                        break;
                    }
                }
                if(settings.get("crosslinkEnabled").equals("true")) {
                    checkCrosslinks(sat, currentTime, earliestStopTime, planExec.getRewardGridUpdates());
                }
            }
            currentTime = earliestStopTime;
        }
        System.out.println("Done!");
        double totalReward = 0.0;
        double naiveReward = 0.0;
        int observationCount = 0;
        int naiveObservationCount = 0;
        int chargeCount = 0;
        int naiveChargeCount = 0;
        int imagingCount = 0;
        int naiveImagingCount = 0;
        int downlinkCount = 0;
        int naiveDownlinkCount = 0;
        Map<GeodeticPoint,Integer> chlorophyllObsTracker = new HashMap<>();
        for (String sat : downlinkEvents.keySet()) {
            totalReward = totalReward + rewardDownlinked.get(sat);
            naiveReward = naiveReward + naiveRewardDownlinked.get(sat);
            observationCount += downlinkedChlorophyllEvents.get(sat).size();
            naiveObservationCount += naiveDownlinkedChlorophyllEvents.get(sat).size();
            for(ChlorophyllEvent chlEvent : downlinkedChlorophyllEvents.get(sat)) {
                if(!chlorophyllObsTracker.containsKey(chlEvent.getLocation())) {
                    chlorophyllObsTracker.put(chlEvent.getLocation(),1);
                } else {
                    chlorophyllObsTracker.put(chlEvent.getLocation(),chlorophyllObsTracker.get(chlEvent.getLocation())+1);
                }
            }
            for (SatelliteAction sa : actionsTaken.get(sat)) {
                switch (sa.getActionType()) {
                    case "charge" -> chargeCount++;
                    case "imaging" -> imagingCount++;
                    case "downlink" -> downlinkCount++;
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
        results.put("smart",totalReward);
        results.put("naive",naiveReward);
        results.put("chl count smart",(double)observationCount);
        results.put("chl count naive",(double)naiveObservationCount);
        Map<GeodeticPoint,Integer> omniscientChlorophyllObsTracker = new HashMap<>();
        ArrayList<GeodeticPoint> gps = new ArrayList<>(globalRewardGrid.keySet());
        int count = 0;
        for (GeodeticPoint gp : gps) {
            if(processImage(gp)) {
                count++;
            }
        }
        System.out.println("Total number of chlorophyll bloom GPs: "+count);
        for(String sat : satList) {
            ArrayList<Observation> obsList = observationEvents.get(sat);
            for(Observation obs : obsList) {
                if(processImage(obs.getObservationPoint())) {
                    if(!omniscientChlorophyllObsTracker.containsKey(obs.getObservationPoint())) {
                        omniscientChlorophyllObsTracker.put(obs.getObservationPoint(),1);
                    } else {
                        omniscientChlorophyllObsTracker.put(obs.getObservationPoint(),omniscientChlorophyllObsTracker.get(obs.getObservationPoint())+1);
                    }
                }
            }
        }
        if(debug) {
            System.out.println("Charge count: "+chargeCount);
            System.out.println("Naive charge count: "+naiveChargeCount);
            System.out.println("Imaging count: "+imagingCount);
            System.out.println("Naive imaging count: "+naiveImagingCount);
            System.out.println("Downlink count: "+downlinkCount);
            System.out.println("Naive downlink count: "+naiveDownlinkCount);
            System.out.println("Total reward: "+totalReward);
            System.out.println("Naive reward: "+naiveReward);
            System.out.println("Total count: "+observationCount);
            System.out.println("Naive count: "+naiveObservationCount);
        }
        long end = System.nanoTime();
        System.out.printf("Took %.4f sec\n", (end - start) / Math.pow(10, 9));
        return results;
    }

//    public SimulatorParallel() {
//
//    }

    public void updateGlobalRewardGrid(Map<GeodeticPoint,ChlorophyllEvent> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            globalRewardGrid.put(gp,chlReward);
        }
        globalRewardGridUpdates.add(updates);
    }
    public void updateNaiveGlobalRewardGrid(Map<GeodeticPoint,ChlorophyllEvent> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            naiveGlobalRewardGrid.put(gp,chlReward);
        }
        naiveGlobalRewardGridUpdates.add(updates);
    }
    public void updateCentralRewardGrid(Map<GeodeticPoint,ChlorophyllEvent> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            centralRewardGrid.put(gp,chlReward);
        }
    }
    public void updateLocalRewardGrid(String sat, Map<GeodeticPoint,ChlorophyllEvent> updates) {
        Map<GeodeticPoint,Double> oldRewardGrid = localRewardGrids.get(sat);
        for(GeodeticPoint gp : updates.keySet()) {
            oldRewardGrid.put(gp,chlReward);
        }
        localRewardGrids.put(sat,oldRewardGrid);
    }

    public void checkCrosslinks(String sat, double currentTime, double endTime, Map<GeodeticPoint,ChlorophyllEvent> updates) {
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

    public void loadChlorophyll() {
        List<List<String>> chlorophyllBaselines = new ArrayList<>();
        List<List<String>> chlorophyllRecents = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/chlorophyll_baseline.csv"))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                if(count == 0) {
                    count++;
                    continue;
                }
                String[] values = line.split(",");
                chlorophyllBaselines.add(Arrays.asList(values));
            }
        } catch (Exception e) {
            System.out.println("Exception occurred in loadCoveragePoints: " + e);
        }
        for (List<String> chlorophyllBaseline : chlorophyllBaselines) {
            double lon = Math.toRadians(parseDouble(chlorophyllBaseline.get(0)));
            double lat = Math.toRadians(parseDouble(chlorophyllBaseline.get(1)));
            double mean = parseDouble(chlorophyllBaseline.get(2));
            double sd = parseDouble(chlorophyllBaseline.get(3));
            GeodeticPoint chloroPoint = new GeodeticPoint(lat, lon, 0.0);
            chlorophyllLimits.put(chloroPoint, mean + sd);
        }
        try (BufferedReader br = new BufferedReader(new FileReader("./src/test/resources/chlorophyll_recent.csv"))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                chlorophyllRecents.add(Arrays.asList(values));
            }
        } catch (Exception e) {
            System.out.println("Exception occurred in loadCoveragePoints: " + e);
        }
        for (int i = 0; i < chlorophyllRecents.size(); i++) {
            double lon = Math.toRadians(parseDouble(chlorophyllBaselines.get(i).get(0)));
            double lat = Math.toRadians(parseDouble(chlorophyllBaselines.get(i).get(1)));
            double chl = parseDouble(chlorophyllRecents.get(i).get(2));
            GeodeticPoint chloroPoint = new GeodeticPoint(lat, lon, 0.0);
            currentChlorophyll.put(chloroPoint, chl);
        }
    }
    public boolean processImage(GeodeticPoint location) {
        double limit = 0;
        double current = 0;
        for(GeodeticPoint gp : chlorophyllLimits.keySet()) {
            if(Math.sqrt(Math.pow(location.getLatitude()-gp.getLatitude(),2)+Math.pow(location.getLongitude()-gp.getLongitude(),2)) < 0.00001) {
                limit = chlorophyllLimits.get(gp);
                current = currentChlorophyll.get(gp);
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
