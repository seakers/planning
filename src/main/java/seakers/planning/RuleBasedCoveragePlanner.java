package seakers.planning;

import org.orekit.bodies.GeodeticPoint;
import seakers.orekit.coverage.access.TimeIntervalArray;

import java.util.*;
import java.util.stream.Collectors;

public class RuleBasedCoveragePlanner {
    private ArrayList<SatelliteAction> results;
    private boolean downlinkEnabled;
    private boolean crosslinkEnabled;

    private boolean resources;
    private ArrayList<Observation> sortedObservations;
    private TimeIntervalArray downlinks;
    private Map<String, TimeIntervalArray> crosslinks;
    private Map<String,String> priorityInfo;
    private Map<GeodeticPoint,Double> rewardGrid;
    private Map<String,String> settings;

    public RuleBasedCoveragePlanner(ArrayList<Observation> sortedObservations, TimeIntervalArray downlinks, Map<GeodeticPoint,Double> rewardGrid, SatelliteState initialState, Map<String,String> priorityInfo, Map<String, String> settings) {
        this.sortedObservations = sortedObservations;
        this.downlinks = downlinks;
        this.rewardGrid = rewardGrid;
        this.priorityInfo = new HashMap<>(priorityInfo);
        this.crosslinkEnabled = Boolean.parseBoolean(settings.get("crosslinkEnabled"));
        this.downlinkEnabled = Boolean.parseBoolean(settings.get("downlinkEnabled"));
        this.resources = Boolean.parseBoolean(settings.get("resources"));
        this.settings = settings;
        ArrayList<StateAction> stateActions = greedyPlan(initialState);
        ArrayList<SatelliteAction> observations = new ArrayList<>();
        for (StateAction stateAction : stateActions) {
            observations.add(stateAction.getA());
        }
        results = observations;
    }

    public ArrayList<StateAction> greedyPlan(SatelliteState initialState) {
        ArrayList<StateAction> resultList = new ArrayList<>();
        double estimatedReward = 3e7;
        for(int i = 0; i < 2; i++) {
            //System.out.println("Estimated reward: "+estimatedReward);
            resultList.clear();
            Map<GeodeticPoint,Integer> obsCounts = new HashMap<>();
            for(GeodeticPoint gp : rewardGrid.keySet()) {
                obsCounts.put(gp,0);
            }
            double totalReward = 0;
            boolean moreActions = true;
            SatelliteState s = initialState;
            double lastTime = 0;
            while(moreActions) {
                SatelliteAction bestAction = selectAction(s,estimatedReward);
                if(bestAction==null) {
                    break;
                }
                double newTime = bestAction.gettStart();
                if(bestAction.getLocation() != null) {
                    updateRewardGrid(bestAction.getLocation(),newTime-lastTime,obsCounts);
                    lastTime = newTime;
                }
                StateAction stateAction = new StateAction(s,bestAction);
                s = transitionFunction(s,bestAction);
                resultList.add(stateAction);
                moreActions = !getActionSpace(s).isEmpty();
                totalReward += bestAction.getReward();
            }
            estimatedReward = totalReward;
            System.out.println(estimatedReward);
        }
        return resultList;
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

    void updateRewardGrid(GeodeticPoint location, double elapsedTime, Map<GeodeticPoint,Integer> obsCounts) {
        for(GeodeticPoint gp : obsCounts.keySet()) {
            if(gp.getLatitude() == location.getLatitude()) {
                obsCounts.put(gp,obsCounts.get(gp)+1);
            }
        }
        ArrayList<GeodeticPoint> nearbyPoints = getPointsInFOV(location, new ArrayList<>(rewardGrid.keySet()));
        for(GeodeticPoint gp : rewardGrid.keySet()) {
            if(nearbyPoints.contains(gp)) {
                rewardGrid.put(gp, 0.0);
            } else {
                int count = 0;
                for(GeodeticPoint obs : obsCounts.keySet()) {
                    if(nearbyPoints.contains(obs)) {
                        count = obsCounts.get(obs);
                    }
                }
                rewardGrid.put(gp,(rewardGrid.get(gp)+elapsedTime)/(5*count+1));
            }
            Set<Double> values = new HashSet<>(rewardGrid.values());
            boolean isUnique = values.size() == 1;
            boolean isZero = false;
            if(isUnique) {
                Double[] valueArray = (Double[]) values.toArray();
                if (valueArray[0] == 0.0) {
                    isZero = true;
                }
            }
            if(isUnique && isZero) {
                rewardGrid.replaceAll( (k,v)-> 1.0);
            }
        }
    }

    public SatelliteState transitionFunction(SatelliteState s, SatelliteAction a) {
        double t = a.gettEnd();
        double tPrevious = s.getT();
        ArrayList<SatelliteAction> history = new ArrayList<>(s.getHistory());
        history.add(a);
        double storedImageReward = s.getStoredImageReward();
        double batteryCharge = s.getBatteryCharge();
        double dataStored = s.getDataStored();
        double currentAngle = s.getCurrentAngle();
        switch (a.getActionType()) {
            case "charge":
                batteryCharge = batteryCharge + (a.gettEnd()-s.getT())*Double.parseDouble(settings.get("chargePower")) / 3600;
                break;
            case "imaging":
                currentAngle = a.getAngle();
                batteryCharge = batteryCharge + (a.gettStart()-s.getT())*Double.parseDouble(settings.get("chargePower")) / 3600;
                batteryCharge = batteryCharge - (a.gettEnd()-a.gettStart())*Double.parseDouble(settings.get("cameraOnPower")) / 3600;
                dataStored += 1.0; // 1 Mbps per picture
                break;
            case "downlink":
                dataStored = dataStored - (a.gettEnd() - a.gettStart()) * Double.parseDouble(settings.get("downlinkSpeedMbps"));
                batteryCharge = batteryCharge + (a.gettStart()-s.getT())*Double.parseDouble(settings.get("chargePower")) / 3600;
                batteryCharge = batteryCharge - (a.gettEnd()-a.gettStart())*Double.parseDouble(settings.get("downlinkOnPower")) / 3600;
                if (dataStored < 0) {
                    dataStored = 0;
                }
                break;
        }
        return new SatelliteState(t,tPrevious,history,batteryCharge,dataStored,currentAngle,storedImageReward);
    }

    public SatelliteAction selectAction(SatelliteState s, double estimatedReward) {
        ArrayList<SatelliteAction> possibleActions = getActionSpace(s);
        SatelliteAction bestAction = null;
        double maximum = 0.0;
        //System.out.println(s.getBatteryCharge());
        if(s.getBatteryCharge() < 15 && resources) {
            bestAction = new SatelliteAction(s.getT(),s.getT()+60.0,null,"charge");
            return bestAction;
        }
        outerloop:
        for (SatelliteAction a : possibleActions) {
            switch(a.getActionType()) {
                case("downlink"):
                    if(s.getDataStored() > 90 && resources) {
                        bestAction = a;
                        break outerloop;
                    }
                    if(!resources) {
                        bestAction = a;
                        break outerloop;
                    }
                    break;
                case("imaging"):
                    double rho = (86400.0*7.0-a.gettEnd())/(86400.0*7.0);
                    double e = Math.pow(rho,1) * estimatedReward;
                    double adjustedReward = a.getReward() + e;
//                    Random rand = new Random();
//                    int rand_int = rand.nextInt(1000);
//                    int index = (int)(Math.random() * possibleActions.size());
//                    if(rand_int > 900) {
//                        bestAction = possibleActions.get(index);
//                        break outerloop;
//                    }
                    if(adjustedReward > maximum) {
                        bestAction = a;
                        maximum = adjustedReward;
                    }
                    break;
            }

        }

        return bestAction;
    }

    public ArrayList<SatelliteAction> getActionSpace(SatelliteState s) {
        double currentTime = s.getT();
        ArrayList<SatelliteAction> possibleActions = new ArrayList<>();
        for (Observation obs : sortedObservations) {
            if(obs.getObservationStart() > currentTime) {
                SatelliteAction obsAction = new SatelliteAction(obs.getObservationStart(),obs.getObservationEnd(),obs.getObservationPoint(),"imaging",rewardGrid.get(obs.getObservationPoint()),obs.getObservationAngle());
                if(canSlew(s.getCurrentAngle(),obs.getObservationAngle(),currentTime,obs.getObservationStart())) {
                    possibleActions.add(obsAction);
                }
            }
        }
        if(downlinkEnabled && resources) {
            for (int i = 0; i < downlinks.getRiseAndSetTimesList().length; i = i + 2) {
                if (downlinks.getRiseAndSetTimesList()[i] > currentTime) {
                    SatelliteAction downlinkAction = new SatelliteAction(downlinks.getRiseAndSetTimesList()[i], downlinks.getRiseAndSetTimesList()[i+1], null, "downlink");
                    possibleActions.add(downlinkAction);
                } else if (downlinks.getRiseAndSetTimesList()[i] < currentTime && downlinks.getRiseAndSetTimesList()[i + 1] > currentTime) {
                    SatelliteAction downlinkAction = new SatelliteAction(currentTime, downlinks.getRiseAndSetTimesList()[i + 1], null, "downlink");
                    possibleActions.add(downlinkAction);
                }
            }
        }
        if(!resources) {
            for (int i = 0; i < downlinks.getRiseAndSetTimesList().length; i = i + 2) {
                if (downlinks.getRiseAndSetTimesList()[i] > currentTime && downlinks.getRiseAndSetTimesList()[i] < currentTime+60) {
                    SatelliteAction downlinkAction = new SatelliteAction(downlinks.getRiseAndSetTimesList()[i], downlinks.getRiseAndSetTimesList()[i]+60, null, "downlink");
                    possibleActions.add(downlinkAction);
                }
            }
        }
        possibleActions.sort(new SatelliteAction.TimeComparator());
        return possibleActions;
    }

    public boolean canSlew(double angle1, double angle2, double time1, double time2){
        double slewTorque = 4*Math.abs(angle2-angle1)*0.05/Math.pow(Math.abs(time2-time1),2);
        double maxTorque = Double.parseDouble(settings.get("maxTorque"));
        return !(slewTorque > maxTorque);
    }

    public ArrayList<SatelliteAction> getResults() {
        return results;
    }
}

