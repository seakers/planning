package seakers.planning;

import org.orekit.bodies.GeodeticPoint;
import seakers.orekit.coverage.access.TimeIntervalArray;

import java.util.*;

public class RuleBasedPlanner {
    private ArrayList<SatelliteAction> results;
    private boolean downlinkEnabled;
    private boolean crosslinkEnabled;
    private ArrayList<Observation> sortedObservations;
    private TimeIntervalArray downlinks;
    private Map<String, TimeIntervalArray> crosslinks;
    private Map<String,String> priorityInfo;
    private Map<GeodeticPoint,Double> rewardGrid;
    private Map<String,String> settings;

    public RuleBasedPlanner(ArrayList<Observation> sortedObservations, TimeIntervalArray downlinks, Map<String,TimeIntervalArray> crosslinks, Map<GeodeticPoint,Double> rewardGrid, SatelliteState initialState, Map<String,String> priorityInfo, Map<String, String> settings) {
        this.sortedObservations = sortedObservations;
        this.downlinks = downlinks;
        this.crosslinks = crosslinks;
        this.rewardGrid = rewardGrid;
        this.priorityInfo = new HashMap<>(priorityInfo);
        this.crosslinkEnabled = Boolean.parseBoolean(settings.get("crosslinkEnabled"));
        this.downlinkEnabled = Boolean.parseBoolean(settings.get("downlinkEnabled"));
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
        SatelliteState s = initialState;
        boolean moreActions = true;
        while(moreActions) {
            SatelliteAction bestAction = selectAction(s);
            if(bestAction==null) {
                break;
            }
            StateAction stateAction = new StateAction(s,bestAction);
            s = transitionFunction(s,bestAction);
            resultList.add(stateAction);
            moreActions = !getActionSpace(s).isEmpty();
        }
        return resultList;
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
            case "imaging" -> {
                currentAngle = a.getAngle();
                batteryCharge = batteryCharge + (a.gettStart()-s.getT())*Double.parseDouble(settings.get("chargePower"));
                batteryCharge = batteryCharge - (a.gettEnd()-a.gettStart())*Double.parseDouble(settings.get("cameraOnPower"));
                dataStored += 1.0; // 1 Mbps per picture
            }
            case "downlink" -> {
                dataStored = dataStored - (a.gettEnd() - a.gettStart()) * Double.parseDouble(settings.get("downlinkSpeedMbps"));
                batteryCharge = batteryCharge + (a.gettStart()-s.getT())*Double.parseDouble(settings.get("chargePower"));
                batteryCharge = batteryCharge - (a.gettEnd()-a.gettStart())*Double.parseDouble(settings.get("downlinkOnPower"));
                if (dataStored < 0) {
                    dataStored = 0;
                }
            }
            case "crosslink" -> {
                batteryCharge = batteryCharge + (a.gettStart()-s.getT())*Double.parseDouble(settings.get("chargePower"));
                batteryCharge = batteryCharge - (a.gettEnd()-a.gettStart())*Double.parseDouble(settings.get("crosslinkOnPower"));
            }
        }
        return new SatelliteState(t,tPrevious,history,batteryCharge,dataStored,currentAngle,storedImageReward);
    }

    public SatelliteAction selectAction(SatelliteState s) {
        ArrayList<SatelliteAction> possibleActions = getActionSpace(s);
        SatelliteAction bestAction = null;
        SatelliteAction crosslinkAction;
        double estimatedReward = 80000;
        double maximum = 0.0;
        if(s.getBatteryCharge() < 15) {
            bestAction = new SatelliteAction(s.getT(),s.getT()+60.0,null,"charge");
            return bestAction;
        }
        outerloop:
        for (SatelliteAction a : possibleActions) {
            switch(a.getActionType()) {
                case("downlink") -> {
                    if(s.getDataStored() > 90) {
                        bestAction = a;
                        break outerloop;
                    }
                }
                case("imaging") -> {
                    double rho = (86400.0-a.gettEnd())/(86400.0);
                    double e = Math.pow(rho,1) * estimatedReward;
                    double adjustedReward = a.getReward() + e;
                    if(adjustedReward > maximum) {
                        bestAction = a;
                        maximum = adjustedReward;
                    }
                }
            }
        }
        for (SatelliteAction a : possibleActions) {
            if (a.getActionType().equals("crosslink") && priorityInfo.get(a.getCrosslinkSat()).equals("new info") && bestAction!=null) {
                crosslinkAction = a;
                if(crosslinkAction.gettEnd() < bestAction.gettStart()) {
                    bestAction = crosslinkAction;
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
        if(downlinkEnabled) {
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
        if(crosslinkEnabled) {
            for (String sat : crosslinks.keySet()) {
                double[] crosslinkTimes = crosslinks.get(sat).getRiseAndSetTimesList();
                for (int i = 0; i < crosslinkTimes.length; i = i + 2) {
                    if (crosslinkTimes[i] >= currentTime) {
                        SatelliteAction crosslinkAction = new SatelliteAction(crosslinkTimes[i], crosslinkTimes[i]+0.1, null, "crosslink", sat);
                        possibleActions.add(crosslinkAction);
                    }
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


