package seakers.planning;

import org.orekit.bodies.GeodeticPoint;
import seakers.orekit.coverage.access.TimeIntervalArray;

import java.io.*;
import java.util.*;

import static java.lang.Double.parseDouble;

public class TimeVaryingEventSimulator {
    private String filepath;
    public Map<String, Map<String, TimeIntervalArray>> crosslinkEvents;
    public Map<String, TimeIntervalArray> downlinkEvents;
    public Map<String, ArrayList<Observation>> observationEvents;
    public Map<String, ArrayList<SatelliteAction>> currentPlans;
    public Map<String, ArrayList<SatelliteAction>> actionsTaken;
    public Map<String, ArrayList<SatelliteAction>> naiveActionsTaken;
    public Map<String, Map<GeodeticPoint, Reward>> localRewardGrids;
    public Map<String, Double> rewardDownlinked;
    public Map<String, Double> naiveRewardDownlinked;
    public Map<GeodeticPoint,Reward> globalRewardGrid;
    public Map<GeodeticPoint,Reward> naiveGlobalRewardGrid;
    public Map<GeodeticPoint,Reward> centralRewardGrid;
    
    public Map<GeodeticPoint,GeophysicalEvent> eventGrid;
    public Map<String, Map<String,String>> crosslinkInfo;
    public ArrayList<Map<GeodeticPoint,EventObservation>> globalRewardGridUpdates;
    public ArrayList<Map<GeodeticPoint,EventObservation>> naiveGlobalRewardGridUpdates;
    public Map<String,ArrayList<EventObservation>> downlinkedGeophysicalEvents;
    public Map<String,ArrayList<EventObservation>> naiveDownlinkedGeophysicalEvents;
    public ArrayList<EventObservation> crosslinkedGeophysicalEvents;
    public Map<String,ArrayList<EventObservation>> imagedGeophysicalEvents;
    public Map<String, SatelliteState> currentStates;
    public Map<GeodeticPoint,Double> geophysicalLimits;
    public Map<GeodeticPoint,Double> currentGeophysical;
    public double chlReward;
    boolean debug;
    public double endTime;
    private Map<String, Double> results;

    public TimeVaryingEventSimulator(Map<String,String> settings) {
        long start = System.nanoTime();
        filepath = "./src/test/resources/plannerData/oneday";
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
        loadEvents();
        loadEventGrid();

        // Create initial plans
        for (String sat : satList) {
            localRewardGrids.put(sat,globalRewardGrid);
            actionsTaken.put(sat,new ArrayList<>());
            SatelliteState satelliteState = new SatelliteState(0,0, new ArrayList<>(),70.0,0.0,0.0,0.0, new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
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
            TimeVaryingEventNaivePlanExecutor planExec = new TimeVaryingEventNaivePlanExecutor(currentStates.get(sat),currentTime,86400.0,currentPlans.get(sat), sat, eventGrid, settings);
            updateNaiveGlobalRewardGrid(planExec.getRewardGridUpdates());
            naiveActionsTaken.put(sat,planExec.getActionsTaken());
            naiveRewardDownlinked.put(sat, planExec.getRewardDownlinked());
            naiveDownlinkedGeophysicalEvents.put(sat,planExec.getEventObservations());
        }

        String planFlag;
        while (currentTime < endTime) {
            System.out.println("Currently at: "+currentTime);
            double earliestStopTime = endTime;
            // determine earliest stop time based on replanning flags
            for (String sat : satList) {
                TimeVaryingEventPlanExecutor planExec = new TimeVaryingEventPlanExecutor(currentStates.get(sat),currentTime,earliestStopTime,currentPlans.get(sat), sat, eventGrid, settings);
                double planTerminationTime = planExec.getStopTime();
                if(planTerminationTime < earliestStopTime) {
                    earliestStopTime = planTerminationTime;
                }
            }
            for (String sat : satList) {
                TimeVaryingEventPlanExecutor planExec = new TimeVaryingEventPlanExecutor(currentStates.get(sat), currentTime, earliestStopTime, currentPlans.get(sat), sat, eventGrid, settings);
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
                        System.out.println(planExec.getRewardDownlinked());
                        rewardDownlinked.put(sat, rewardDownlinked.get(sat) + planExec.getRewardDownlinked());
                        ArrayList<EventObservation> tempList = new ArrayList<>(downlinkedGeophysicalEvents.get(sat));
                        tempList.addAll(imagedGeophysicalEvents.get(sat));
                        downlinkedGeophysicalEvents.put(sat, tempList);
                        imagedGeophysicalEvents.put(sat, new ArrayList<>());
                        localRewardGrids.put(sat, centralRewardGrid);
                        makePlan(sat,settings);
                        break;
                    }
                    case "image": {
                        updateLocalRewardGrid(sat, planExec.getRewardGridUpdates());
                        updateGlobalRewardGrid(planExec.getRewardGridUpdates());
                        imagedGeophysicalEvents.get(sat).addAll(planExec.getEventObservations());
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
        computeStatistics("Reactive",rewardDownlinked,downlinkedGeophysicalEvents,actionsTaken);
        computeStatistics("Non-reactive",naiveRewardDownlinked,naiveDownlinkedGeophysicalEvents,naiveActionsTaken);
        long end = System.nanoTime();
        System.out.printf("Took %.4f sec\n", (end - start) / Math.pow(10, 9));
    }

    public void computeStatistics(String flag, Map<String,Double> downlinkedReward, Map<String,ArrayList<EventObservation>> geophysicalEventsDownlinked, Map<String, ArrayList<SatelliteAction>> takenActions) {
        double totalReward = 0.0;
        int observationCount = 0;
        int chargeCount = 0;
        int imagingCount = 0;
        int downlinkCount = 0;
        ArrayList<String> satList = new ArrayList<>(downlinkEvents.keySet());
        Map<GeodeticPoint,ArrayList<EventObservation>> geophysicalObsTracker = new HashMap<>();
        for (String sat : downlinkEvents.keySet()) {
            totalReward = totalReward + downlinkedReward.get(sat);
            observationCount += geophysicalEventsDownlinked.get(sat).size();
            for(EventObservation obsEvent : geophysicalEventsDownlinked.get(sat)) {
                if(!geophysicalObsTracker.containsKey(obsEvent.getLocation())) {
                    ArrayList<EventObservation> chlEventsPerPoint = new ArrayList<>();
                    chlEventsPerPoint.add(obsEvent);
                    geophysicalObsTracker.put(obsEvent.getLocation(),chlEventsPerPoint);
                } else {
                    ArrayList<EventObservation> chlEventsPerPoint = geophysicalObsTracker.get(obsEvent.getLocation());
                    chlEventsPerPoint.add(obsEvent);
                    geophysicalObsTracker.put(obsEvent.getLocation(),chlEventsPerPoint);
                }
            }
            for (SatelliteAction sa : takenActions.get(sat)) {
                switch (sa.getActionType()) {
                    case "charge":
                        chargeCount++;
                        break;
                    case "imaging":
                        imagingCount++;
                        break;
                    case "downlink":
                        downlinkCount++;
                        break;
                }
            }
        }
        results.put(flag,totalReward);
        results.put("chl count "+flag,(double)observationCount);
        Map<GeodeticPoint,Integer> omniscientGeophysicalObsTracker = new HashMap<>();
        ArrayList<GeodeticPoint> gps = new ArrayList<>(globalRewardGrid.keySet());
//        int count = 0;
//        for (GeodeticPoint gp : gps) {
//            if(processImage(gp)) {
//                count++;
//            }
//        }
//        System.out.println("Total number of geophysical bloom GPs: "+count);
        for(String sat : satList) {
            ArrayList<Observation> obsList = observationEvents.get(sat);
            for(Observation obs : obsList) {
                if(processImage(obs.getObservationStart(),obs.getObservationPoint())) {
                    if(!omniscientGeophysicalObsTracker.containsKey(obs.getObservationPoint())) {
                        omniscientGeophysicalObsTracker.put(obs.getObservationPoint(),1);
                    } else {
                        omniscientGeophysicalObsTracker.put(obs.getObservationPoint(),omniscientGeophysicalObsTracker.get(obs.getObservationPoint())+1);
                    }
                }
            }
        }
        int repeatCount = 0;
        int repeatSum = 0;
        int uniqueCount = 0;
        for(GeodeticPoint gp : geophysicalObsTracker.keySet()) {
            //System.out.println(geophysicalObsTracker.get(gp).size());
            int numRepeatObservations = geophysicalObsTracker.get(gp).size() - 1;
            if(numRepeatObservations > 0) {
                repeatCount++;
            } else {
                uniqueCount++;
            }
            repeatSum+=numRepeatObservations;
        }
        double repeatAvg = (double)repeatSum / geophysicalObsTracker.size();
        System.out.println(flag+" charge count: "+chargeCount);
        System.out.println(flag+" imaging count: "+imagingCount);
        System.out.println(flag+" downlink count: "+downlinkCount);
        System.out.println(flag+" total reward: "+totalReward);
        System.out.println(flag+" total count: "+observationCount);
        System.out.println(flag+" average repeat observations: "+repeatAvg);
        System.out.println(flag+" number of revisited locations: "+repeatCount);
        System.out.println(flag+" number of single observations: "+uniqueCount);
    }

    public void updateGlobalRewardGrid(Map<GeodeticPoint,EventObservation> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            Reward reward = new Reward(chlReward,updates.get(gp));
            globalRewardGrid.put(gp,reward);
        }
        globalRewardGridUpdates.add(updates);
    }
    public void updateNaiveGlobalRewardGrid(Map<GeodeticPoint,EventObservation> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            Reward reward = new Reward(chlReward,updates.get(gp));
            naiveGlobalRewardGrid.put(gp,reward);
        }
        naiveGlobalRewardGridUpdates.add(updates);
    }
    public void updateCentralRewardGrid(Map<GeodeticPoint,EventObservation> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            Reward reward = new Reward(chlReward,updates.get(gp));
            centralRewardGrid.put(gp,reward);
        }
    }
    public void updateLocalRewardGrid(String sat, Map<GeodeticPoint,EventObservation> updates) {
        Map<GeodeticPoint,Reward> oldRewardGrid = localRewardGrids.get(sat);
        for(GeodeticPoint gp : updates.keySet()) {
            Reward reward = new Reward(chlReward,updates.get(gp));
            oldRewardGrid.put(gp,reward);
        }
        localRewardGrids.put(sat,oldRewardGrid);
    }

    public void checkCrosslinks(String sat, double currentTime, double endTime, Map<GeodeticPoint,EventObservation> updates) {
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

    public void loadEvents() {
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
            System.out.println("Exception occurred in loadEvents: " + e);
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
    public boolean processImage(double time, GeodeticPoint location) {
        if(eventGrid.containsKey(location)) {
            if(eventGrid.get(location).getStartTime() < time && time < eventGrid.get(location).getEndTime()) {
                return true;
            } else {
                return false;
            }
        } else {
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

                Map<GeodeticPoint,Double> inputRewardGrid =  (Map<GeodeticPoint,Double>) oi.readObject();
                EventObservation dummyEventObs = new EventObservation(null,86400.0,1.0);
                Reward reward = new Reward(1.0,dummyEventObs);
                globalRewardGrid = new HashMap<>();
                for(GeodeticPoint gp : inputRewardGrid.keySet()) {
                    globalRewardGrid.put(gp,reward);
                }
                naiveGlobalRewardGrid = new HashMap<>(globalRewardGrid);
                centralRewardGrid = new HashMap<>(globalRewardGrid);

                oi.close();
                fi.close();
            }
        } catch (Exception e) {
            System.out.println("Exception in loadRewardGrid: "+e.getMessage());
        }
    }

    public void loadEventGrid() {
        try{
            eventGrid = new HashMap<>();
            for(GeodeticPoint gp : globalRewardGrid.keySet()) {
                Random random = new Random();
                int percentOfGPsWithEvent = 20;

                if(random.nextInt(100) < percentOfGPsWithEvent) {
                    double duration = random.nextGaussian()*(3600*2.0)+(3600*8.0); // 8 hour mean, 2 hour std dev.
                    double startTime = random.nextDouble()*86400.0;
                    double endTime = startTime + duration;
                    double value = random.nextDouble()*100.0;
                    GeophysicalEvent event = new GeophysicalEvent(gp, startTime, endTime, value);
                    eventGrid.put(gp,event);
                }
            }
        } catch (Exception e) {
            System.out.println("Exception in loadEventGrid: "+e.getMessage());
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
                TimeVaryingEventRuleBasedPlanner ruleBasedPlanner = new TimeVaryingEventRuleBasedPlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                currentPlans.put(sat, ruleBasedPlanner.getResults());
                break;
//            case "mcts" -> {
//                MCTSPlanner mctsPlanner = new MCTSPlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
//                currentPlans.put(sat, mctsPlanner.getResults());
//            }
//            case "dumbMcts" -> {
//                DumbMCTSPlanner dumbMctsPlanner = new DumbMCTSPlanner(observationEvents.get(sat), downlinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
//                currentPlans.put(sat, dumbMctsPlanner.getResults());
//            }
        }
    }

    public Map<String, Double> getResults() { return results; }

}
