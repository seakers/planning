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
    private String filepath;
    public Map<String, Map<String, TimeIntervalArray>> crosslinkEvents;
    public Map<String, TimeIntervalArray> downlinkEvents;
    public Map<String, ArrayList<Observation>> observationEvents;
    public Map<String, ArrayList<SatelliteAction>> currentPlans;
    public Map<String, ArrayList<SatelliteAction>> actionsTaken;
    public Map<String, Map<GeodeticPoint, Double>> localRewardGrids;
    public Map<GeodeticPoint,Double> globalRewardGrid;
    public Map<GeodeticPoint,Double> centralRewardGrid;
    public ArrayList<Map<GeodeticPoint,Double>> globalRewardGridUpdates;
    public Map<String, SatelliteState> currentStates;
    public double totalImageProcessingTime;

    public Planner() {
        long start = System.nanoTime();
        filepath = "./src/test/resources/plannerData/tenthday";
        loadCrosslinks();
        loadDownlinks();
        loadObservations();
        loadRewardGrid();
        totalImageProcessingTime = 0.0;
        localRewardGrids = new HashMap<>();
        actionsTaken = new HashMap<>();
        currentPlans = new HashMap<>();
        currentStates = new HashMap<>();
        globalRewardGridUpdates = new ArrayList<>();
        for (String sat : downlinkEvents.keySet()) {
            localRewardGrids.put(sat,globalRewardGrid);
            actionsTaken.put(sat,new ArrayList<>());
            SatelliteState satelliteState = new SatelliteState(0,0, new ArrayList<>(),70.0,0.0,0.0,0.0);
            currentStates.put(sat,satelliteState);
            MCTSPlanner mctsPlanner = new MCTSPlanner(observationEvents.get(sat),downlinkEvents.get(sat),crosslinkEvents.get(sat),localRewardGrids.get(sat),currentStates.get(sat), 0.0);
            ArrayList<SatelliteAction> results = mctsPlanner.getResults();
            currentPlans.put(sat,results);
            System.out.println("Done with initial plan for "+sat);
        }
        System.out.println("Done with initial plans");
        double currentTime = 0.0;

        String planFlag = "";
        String replanSat = "";
        while (currentTime < 86400.0*0.1) {
            System.out.println("Currently at: "+currentTime);
            double earliestStopTime = 86400.0*0.1;
            for (String sat : downlinkEvents.keySet()) {
                PlanExecutor planExec = new PlanExecutor(currentStates.get(sat),currentTime,earliestStopTime,currentPlans.get(sat));
                totalImageProcessingTime = totalImageProcessingTime + planExec.getImageProcessingTime();
                double planTerminationTime = planExec.getStopTime();
                if(planTerminationTime < earliestStopTime) {
                    earliestStopTime = planTerminationTime;
                    planFlag = planExec.getReplanFlag();
                    replanSat = sat;
                }
            }
            for (String sat : downlinkEvents.keySet()) {
                PlanExecutor planExec = new PlanExecutor(currentStates.get(sat),currentTime,earliestStopTime,currentPlans.get(sat));
                totalImageProcessingTime = totalImageProcessingTime + planExec.getImageProcessingTime();
                ArrayList<SatelliteAction> actionsSoFar = actionsTaken.get(sat);
                actionsSoFar.addAll(planExec.getActionsTaken());
                actionsTaken.put(sat,actionsSoFar);
                currentStates.put(sat,planExec.getReturnState());
                if(Objects.equals(sat, replanSat)) {
                    if (planFlag.equals("downlink")) {
                        updateCentralRewardGrid(planExec.getRewardGridUpdates());
                        updateGlobalRewardGrid(planExec.getRewardGridUpdates());
                        localRewardGrids.put(sat,centralRewardGrid);
                        MCTSPlanner mctsPlanner = new MCTSPlanner(observationEvents.get(sat),downlinkEvents.get(sat),crosslinkEvents.get(sat),localRewardGrids.get(sat),currentStates.get(sat), 0.0);
                        ArrayList<SatelliteAction> results = mctsPlanner.getResults();
                        currentPlans.put(sat,results);
                    } else if (planFlag.equals("image")) {
                        updateLocalRewardGrid(sat,planExec.getRewardGridUpdates());
                        updateGlobalRewardGrid(planExec.getRewardGridUpdates());
                        MCTSPlanner mctsPlanner = new MCTSPlanner(observationEvents.get(sat),downlinkEvents.get(sat),crosslinkEvents.get(sat),localRewardGrids.get(sat),currentStates.get(sat), 100.0);
                        ArrayList<SatelliteAction> results = mctsPlanner.getResults();
                        currentPlans.put(sat,results);
                    } else {
                        System.out.println("Crosslink! Whoa!");
                        updateLocalRewardGrid(planFlag,planExec.getRewardGridUpdates());
                        updateLocalRewardGrid(sat,planExec.getRewardGridUpdates());
                        updateGlobalRewardGrid(planExec.getRewardGridUpdates());
                        MCTSPlanner mctsPlanner = new MCTSPlanner(observationEvents.get(sat),downlinkEvents.get(sat),crosslinkEvents.get(sat),localRewardGrids.get(sat),currentStates.get(sat), 0.0);
                        ArrayList<SatelliteAction> results = mctsPlanner.getResults();
                        currentPlans.put(sat,results);
                        MCTSPlanner crosslinkSatPlanner = new MCTSPlanner(observationEvents.get(planFlag),downlinkEvents.get(planFlag),crosslinkEvents.get(planFlag),localRewardGrids.get(planFlag),currentStates.get(planFlag), 0.0);
                        ArrayList<SatelliteAction> crosslinkResults = crosslinkSatPlanner.getResults();
                        currentPlans.put(planFlag,crosslinkResults);
                    }
                }
            }
            currentTime = earliestStopTime;
        }
        System.out.println("Done!");
        double totalReward = 0.0;
        for (String sat : downlinkEvents.keySet()) {
            System.out.println("Actions taken for satellite "+sat+": ");
            System.out.println(actionsTaken.get(sat));
            for (SatelliteAction sa : actionsTaken.get(sat)) {
                if(sa!=null && sa.getLocation()!=null) {
                    GeodeticPoint observedGP = sa.getLocation();
                    totalReward = totalReward + globalRewardGrid.get(observedGP);
                }
            }
        }
        System.out.println("Total reward: "+totalReward);
        for (Map<GeodeticPoint,Double> updates : globalRewardGridUpdates) {
            for (GeodeticPoint gp : updates.keySet()) {
                System.out.println("Update at: "+gp.toString()+" to value: "+updates.get(gp));
            }
        }
        System.out.println("Total image processing time: "+totalImageProcessingTime);
        long end = System.nanoTime();
        System.out.printf("Took %.4f sec\n", (end - start) / Math.pow(10, 9));
    }

    public void updateGlobalRewardGrid(Map<GeodeticPoint,Double> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            globalRewardGrid.put(gp,updates.get(gp));
        }
        globalRewardGridUpdates.add(updates);
    }
    public void updateCentralRewardGrid(Map<GeodeticPoint,Double> updates) {
        for(GeodeticPoint gp : updates.keySet()) {
            centralRewardGrid.put(gp,updates.get(gp));
        }
    }

    public void updateLocalRewardGrid(String sat, Map<GeodeticPoint,Double> updates) {
        Map<GeodeticPoint,Double> oldRewardGrid = localRewardGrids.get(sat);
        for(GeodeticPoint gp : updates.keySet()) {
            oldRewardGrid.put(gp,updates.get(gp));
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
                centralRewardGrid = globalRewardGrid;

                oi.close();
                fi.close();
            }
        } catch (Exception e) {
            System.out.println("Exception in loadRewardGrid: "+e.getMessage());
        }
    }


}
