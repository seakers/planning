package seakers.planning;

import org.orekit.bodies.GeodeticPoint;
import seakers.orekit.coverage.access.TimeIntervalArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Planner {
    private final String filepath;
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
    public ArrayList<ChlorophyllEvent> naiveDownlinkedChlorophyllEvents;
    public ArrayList<ChlorophyllEvent> totalDownlinkedChlorophyllEvents;
    public ArrayList<ChlorophyllEvent> crosslinkedChlorophyllEvents;
    public Map<String,ArrayList<ChlorophyllEvent>> imagedChlorophyllEvents;
    public Map<String, SatelliteState> currentStates;
    public double totalImageProcessingTime;
    public double chlReward;
    private Map<String, Double> results;

    public Planner(Map<String,String> settings) {
        long start = System.nanoTime();
        filepath = "./src/test/resources/plannerData/oneday";
        loadCrosslinks();
        loadDownlinks();
        loadObservations();
        loadRewardGrid();
        results = new HashMap<>();
        chlReward = 100;
        totalImageProcessingTime = 0.0;
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
        naiveDownlinkedChlorophyllEvents = new ArrayList<>();
        totalDownlinkedChlorophyllEvents = new ArrayList<>();
        crosslinkedChlorophyllEvents = new ArrayList<>();
        imagedChlorophyllEvents = new HashMap<>();
        ArrayList<String> satList = new ArrayList<>(downlinkEvents.keySet());
        crosslinkInfo = crosslinkInfoInitialize(satList);
        for (String sat : satList) {
            localRewardGrids.put(sat,globalRewardGrid);
            actionsTaken.put(sat,new ArrayList<>());
            SatelliteState satelliteState = new SatelliteState(0,0, new ArrayList<>(),70.0,0.0,0.0,0.0, new ArrayList<>(),new ArrayList<>(),new ArrayList<>());
            currentStates.put(sat,satelliteState);
            GreedyPlanner GreedyPlanner = new GreedyPlanner(observationEvents.get(sat),downlinkEvents.get(sat),crosslinkEvents.get(sat),localRewardGrids.get(sat),currentStates.get(sat), crosslinkInfo.get(sat), settings);
            ArrayList<SatelliteAction> results = GreedyPlanner.getResults();
            currentPlans.put(sat,results);
            rewardDownlinked.put(sat,0.0);
            imagedChlorophyllEvents.put(sat, new ArrayList<>());
            downlinkedChlorophyllEvents.put(sat, new ArrayList<>());
            System.out.println("Done with initial plan for "+sat);
        }
        double currentTime = 0.0;

        for (String sat : satList) {
            NaivePlanExecutor planExec = new NaivePlanExecutor(currentStates.get(sat),currentTime,86400.0,currentPlans.get(sat), sat);
            updateNaiveGlobalRewardGrid(planExec.getRewardGridUpdates());
            naiveActionsTaken.put(sat,planExec.getActionsTaken());
            naiveRewardDownlinked.put(sat, planExec.getRewardDownlinked());
            naiveDownlinkedChlorophyllEvents.addAll(planExec.getChlorophyllEvents());
        }
        PlanExecutor tempPlanExec = new PlanExecutor(currentStates.get("smallsat00"),currentTime,86400.0,currentPlans.get("smallsat00"),"smallsat00");
        ArrayList<GeodeticPoint> chlorophyllLocations = tempPlanExec.getChlorophyllLocations();
        Map<GeodeticPoint,Double> omniscientGrid = new HashMap<>(globalRewardGrid);

//        for (String sat : satList) {
//            for(Observation obs : observationEvents.get(sat)) {
//                for(GeodeticPoint gp : chlorophyllLocations) {
//                    if(getDist(gp,obs.getObservationPoint()) < 0.00001) {
//                        //System.out.println("Sat "+sat+" sees "+gp+" at "+obs.getObservationStart());
//                        omniscientGrid.put(obs.getObservationPoint(),chlReward);
//                    }
//                }
//            }
//        }
//        for (String sat : satList) {
//            GreedyPlanner GreedyPlanner = new GreedyPlanner(observationEvents.get(sat), downlinkEvents.get(sat), crosslinkEvents.get(sat), omniscientGrid, currentStates.get(sat), crosslinkInfo.get(sat), settings);
//            ArrayList<SatelliteAction> results = GreedyPlanner.getResults();
//            currentPlans.put(sat, results);
//            NaivePlanExecutor planExec = new NaivePlanExecutor(currentStates.get(sat),currentTime,86400.0,currentPlans.get(sat), sat);
//            updateGlobalRewardGrid(planExec.getRewardGridUpdates());
//            actionsTaken.put(sat,planExec.getActionsTaken());
//            rewardDownlinked.put(sat, planExec.getRewardDownlinked());
//            totalDownlinkedChlorophyllEvents.addAll(planExec.getChlorophyllEvents());
//        }
        String planFlag;
        while (currentTime < 86400.0) {
            System.out.println("Currently at: "+currentTime);
            double earliestStopTime = 86400.0;
            for (String sat : satList) {
                PlanExecutor planExec = new PlanExecutor(currentStates.get(sat),currentTime,earliestStopTime,currentPlans.get(sat), sat);
                double planTerminationTime = planExec.getStopTime();
                if(planTerminationTime < earliestStopTime) {
                    earliestStopTime = planTerminationTime;
                }
            }
            for (String sat : satList) {
                PlanExecutor planExec = new PlanExecutor(currentStates.get(sat), currentTime, earliestStopTime, currentPlans.get(sat), sat);
                ArrayList<SatelliteAction> actionsSoFar = actionsTaken.get(sat);
                actionsSoFar.addAll(planExec.getActionsTaken());
                actionsTaken.put(sat, actionsSoFar);
                currentStates.put(sat, planExec.getReturnState());
                planFlag = planExec.getReplanFlag();
                switch (planFlag) {
                    case "":
                        break;
                    case "downlink": {
                        updateCentralRewardGrid(planExec.getRewardGridUpdates(), earliestStopTime);
                        updateGlobalRewardGrid(planExec.getRewardGridUpdates());
                        rewardDownlinked.put(sat, rewardDownlinked.get(sat) + planExec.getRewardDownlinked());
                        ArrayList<ChlorophyllEvent> tempList = new ArrayList<>(downlinkedChlorophyllEvents.get(sat));
                        tempList.addAll(imagedChlorophyllEvents.get(sat));
                        downlinkedChlorophyllEvents.put(sat, tempList);
                        imagedChlorophyllEvents.put(sat, new ArrayList<>());
                        localRewardGrids.put(sat, centralRewardGrid);
                        GreedyPlanner GreedyPlanner = new GreedyPlanner(observationEvents.get(sat), downlinkEvents.get(sat), crosslinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                        ArrayList<SatelliteAction> results = GreedyPlanner.getResults();
                        currentPlans.put(sat, results);
                        break;
                    }
                    case "image": {
                        updateLocalRewardGrid(sat, planExec.getRewardGridUpdates(), earliestStopTime);
                        updateGlobalRewardGrid(planExec.getRewardGridUpdates());
                        imagedChlorophyllEvents.get(sat).addAll(planExec.getChlorophyllEvents());
                        for (String otherSat : crosslinkInfo.get(sat).keySet()) {
                            crosslinkInfo.get(sat).put(otherSat, "new info");
                        }
                        GreedyPlanner GreedyPlanner = new GreedyPlanner(observationEvents.get(sat), downlinkEvents.get(sat), crosslinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                        ArrayList<SatelliteAction> results = GreedyPlanner.getResults();
                        currentPlans.put(sat, results);
                        break;
                    }
                    default: {
                        updateLocalRewardGrid(planFlag, planExec.getRewardGridUpdates(), earliestStopTime);
                        updateLocalRewardGrid(sat, planExec.getRewardGridUpdates(), earliestStopTime);
                        updateGlobalRewardGrid(planExec.getRewardGridUpdates());
                        crosslinkedChlorophyllEvents.addAll(planExec.getChlorophyllEvents());
                        for (String otherSat : crosslinkInfo.get(planFlag).keySet()) {
                            crosslinkInfo.get(planFlag).put(otherSat, "new info");
                        }
                        crosslinkInfo.get(sat).put(planFlag, "no new info");
                        GreedyPlanner GreedyPlanner = new GreedyPlanner(observationEvents.get(sat), downlinkEvents.get(sat), crosslinkEvents.get(sat), localRewardGrids.get(sat), currentStates.get(sat), crosslinkInfo.get(sat), settings);
                        ArrayList<SatelliteAction> results = GreedyPlanner.getResults();
                        currentPlans.put(sat, results);
                        GreedyPlanner crosslinkSatPlanner = new GreedyPlanner(observationEvents.get(planFlag), downlinkEvents.get(planFlag), crosslinkEvents.get(planFlag), localRewardGrids.get(planFlag), currentStates.get(planFlag), crosslinkInfo.get(sat), settings);
                        ArrayList<SatelliteAction> crosslinkResults = crosslinkSatPlanner.getResults();
                        currentPlans.put(planFlag, crosslinkResults);
                        break;
                    }
                }
            }
            currentTime = earliestStopTime;
        }
        System.out.println("Done!");
        double totalReward = 0.0;
        double naiveReward = 0.0;
        int observationCount = 0;
        int naiveObservationCount;
        int chargeCount = 0;
        int naiveChargeCount = 0;
        int imagingCount = 0;
        int naiveImagingCount = 0;
        int downlinkCount = 0;
        int naiveDownlinkCount = 0;
        for (String sat : downlinkEvents.keySet()) {
            //System.out.println("Actions taken for satellite "+sat+": ");
//            System.out.println(actionsTaken.get(sat));
//            for (SatelliteAction sa : actionsTaken.get(sat)) {
//                if(sa!=null && sa.getLocation()!=null) {
//                    GeodeticPoint observedGP = sa.getLocation();
//                    totalReward = totalReward + globalRewardGrid.get(observedGP);
//                    if(sa.getActionType().equals("imaging")) {
//                        observationCount++;
//                    }
//                }
//            }
//            for (SatelliteAction sa : naiveActionsTaken.get(sat)) {
//                if(sa!=null && sa.getLocation()!=null) {
//                    GeodeticPoint observedGP = sa.getLocation();
//                    naiveReward = naiveReward + naiveGlobalRewardGrid.get(observedGP);
//                    if(sa.getActionType().equals("imaging")) {
//                        naiveObservationCount++;
//                    }
//                }
//            }
//            for (ChlorophyllEvent ce : currentStates.get(sat).getChlorophyllEvents()) {
//                System.out.println(ce.getEventLog());
//            }
//            for (String dl : currentStates.get(sat).getDownlinkLog()) {
//                System.out.println(dl);
//            }
//            for (String cl : currentStates.get(sat).getCrosslinkLog()) {
//                System.out.println(cl);
//            }
            totalReward = totalReward + rewardDownlinked.get(sat);
            naiveReward = naiveReward + naiveRewardDownlinked.get(sat);
            observationCount += downlinkedChlorophyllEvents.get(sat).size();
            for (ChlorophyllEvent ce : imagedChlorophyllEvents.get(sat)) {
                //System.out.println("Chlorophyll event imaged by sat "+sat+" at "+ce.getEventLog());
            }
            for (ChlorophyllEvent ce : downlinkedChlorophyllEvents.get(sat)) {
                //System.out.println("Chlorophyll event downlinked by sat "+sat+" at "+ce.getEventLog());
            }
            for (SatelliteAction sa : actionsTaken.get(sat)) {
                if(sa.getActionType().equals("charge")) {
                    chargeCount++;
                } else if(sa.getActionType().equals("imaging")) {
                    imagingCount++;
                } else if(sa.getActionType().equals("downlink")) {
                    downlinkCount++;
                }
            }
            for (SatelliteAction sa : naiveActionsTaken.get(sat)) {
                if(sa.getActionType().equals("charge")) {
                    naiveChargeCount++;
                } else if(sa.getActionType().equals("imaging")) {
                    naiveImagingCount++;
                } else if(sa.getActionType().equals("downlink")) {
                    naiveDownlinkCount++;
                }
            }
        }
        System.out.println("Charge count: "+chargeCount);
        System.out.println("Naive charge count: "+naiveChargeCount);
        System.out.println("Imaging count: "+imagingCount);
        System.out.println("Naive imaging count: "+naiveImagingCount);
        System.out.println("Downlink count: "+downlinkCount);
        System.out.println("Naive downlink count: "+naiveDownlinkCount);
        naiveObservationCount = naiveDownlinkedChlorophyllEvents.size();
        System.out.println("Total reward: "+totalReward);
        System.out.println("Naive reward: "+naiveReward);
        System.out.println("Total count: "+observationCount);
        System.out.println("Naive count: "+naiveObservationCount);
        results.put("smart",totalReward);
        results.put("naive",naiveReward);
        results.put("chl count smart",(double)observationCount);
        results.put("chl count naive",(double)naiveObservationCount);

//        System.out.println("Total actions: "+observationCount);
//        System.out.println("Naive actions: "+naiveObservationCount);
        for (Map<GeodeticPoint,ChlorophyllEvent> updates : globalRewardGridUpdates) {
            for (GeodeticPoint gp : updates.keySet()) {
                //System.out.println("Update at: "+gp.toString()+" to value: "+updates.get(gp));
            }
        }
        for (Map<GeodeticPoint,ChlorophyllEvent> updates : naiveGlobalRewardGridUpdates) {
            for (GeodeticPoint gp : updates.keySet()) {
               // System.out.println("Naive update at: "+gp.toString()+" to value: "+updates.get(gp));
            }
        }
        long end = System.nanoTime();
        System.out.printf("Took %.4f sec\n", (end - start) / Math.pow(10, 9));
    }

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
    public void updateCentralRewardGrid(Map<GeodeticPoint,ChlorophyllEvent> updates, double currentTime) {
        for(GeodeticPoint gp : updates.keySet()) {
//            if(updates.get(gp).getEndTime() < currentTime) {
//                centralRewardGrid.put(gp,chlReward);
//            }
            centralRewardGrid.put(gp,chlReward);
        }
    }
    public void updateLocalRewardGrid(String sat, Map<GeodeticPoint,ChlorophyllEvent> updates, double currentTime) {
        Map<GeodeticPoint,Double> oldRewardGrid = localRewardGrids.get(sat);
        for(GeodeticPoint gp : updates.keySet()) {
//            if(updates.get(gp).getEndTime() < currentTime)
            oldRewardGrid.put(gp,chlReward);
        }
        localRewardGrids.put(sat,oldRewardGrid);
    }

    public void loadCrosslinks() {
        File directory = new File(filepath);
        Map<String, Map<String, TimeIntervalArray>> cl = new HashMap<>();
        Map<String, TimeIntervalArray> loadedIntervals = new HashMap<>();
        try {
            String[] directories = directory.list(new FilenameFilter() {
                @Override
                public boolean accept(File current, String name) {
                    return new File(current, name).isDirectory();
                }
            });
            for(int i = 0; i < directory.list().length-1; i++) {
                FileInputStream fi = new FileInputStream(new File(filepath+"/"+directories[i]+"/crosslinks.dat"));
                ObjectInputStream oi = new ObjectInputStream(fi);
                loadedIntervals = (Map<String, TimeIntervalArray>) oi.readObject();
//                TimeIntervalArray crosslinkIntervals =  (TimeIntervalArray) oi.readObject();
//                String satelliteName = "satellite"+i;
//                loadedIntervals.put(satelliteName,crosslinkIntervals);
                cl.put(directories[i],loadedIntervals);
                oi.close();
                fi.close();
            }
            crosslinkEvents = cl;
        } catch (Exception e) {
            System.out.println("Exception in loadCrosslinks: "+e);
        }
//        for (String s : loadedIntervals.keySet()) {
//            Map<String, TimeIntervalArray> satelliteEvents = new HashMap<>();
//            for (String q : loadedIntervals.keySet()) {
//                if(q.equals(s)) {
//                    continue;
//                }
//                ArrayList<RiseSetTime> primary = loadedIntervals.get(s).getRiseSetTimes();
//                ArrayList<RiseSetTime> secondary = loadedIntervals.get(q).getRiseSetTimes();
//                double[] xd = loadedIntervals.get(s).getRiseAndSetTimesList();
//                double[] xe = loadedIntervals.get(q).getRiseAndSetTimesList();
//                for (int i = 1; i < primary.size()-1; i++) {
//                    for(int j = 1; j < secondary.size()-1; j++) {
//                        if(Math.abs(secondary.get(j).getTime()-2269.4835111461293) < 5) {
//                            System.out.println("uhhhh");
//                        }
//                        if(primary.get(i).getTime() == secondary.get(j).getTime() && primary.get(i).isRise() && secondary.get(j).isRise()) {
//                            for(int x = i+1; x < primary.size()-1; x++) {
//                                for (int y = j+1; y < secondary.size()-1; y++) {
//                                    if(primary.get(x).getTime() == secondary.get(y).getTime() && !primary.get(x).isRise() && !secondary.get(y).isRise()) {
//                                        TimeIntervalArray tia = new TimeIntervalArray(loadedIntervals.get(s).getHead(),loadedIntervals.get(s).getTail());
//                                        tia.addRiseTime(primary.get(i).getTime());
//                                        tia.addSetTime(primary.get(x).getTime());
//                                        System.out.println("Crosslink starting at: "+primary.get(i).getTime()+", ending at: "+primary.get(x).getTime());
//                                        primary.remove(i);
//                                        primary.remove(x-1);
//                                        secondary.remove(j);
//                                        secondary.remove(y-1);
//                                        y = secondary.size();
//                                        x = primary.size();
//                                        satelliteEvents.put(q,tia);
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            cl.put(s,satelliteEvents);
//        }
//        crosslinkEvents = cl;
    }

    public void loadDownlinks() {
        File directory = new File(filepath);
        Map<String, TimeIntervalArray> dl = new HashMap<>();
        try{
            String[] directories = directory.list(new FilenameFilter() {
                @Override
                public boolean accept(File current, String name) {
                    return new File(current, name).isDirectory();
                }
            });
            for(int i = 0; i < directory.list().length-1; i++) {
                FileInputStream fi = new FileInputStream(new File(filepath+"/"+directories[i]+"/downlinks.dat"));
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
            String[] directories = directory.list(new FilenameFilter() {
                @Override
                public boolean accept(File current, String name) {
                    return new File(current, name).isDirectory();
                }
            });
            for(int i = 0; i < directory.list().length-1; i++) {
                FileInputStream fi = new FileInputStream(new File(filepath+"/"+directories[i]+"/observations.dat"));
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
                FileInputStream fi = new FileInputStream(new File(filepath+"/coveragePoints.dat"));
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

    public Map<String, Double> getResults() { return results; }

    public double getDist(GeodeticPoint gp1, GeodeticPoint gp2) {
        return Math.sqrt(Math.pow(gp1.getLatitude()-gp2.getLatitude(),2)+Math.pow(gp1.getLongitude()-gp2.getLongitude(),2));
    }

}
