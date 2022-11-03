package seakers.planning;

import org.orekit.bodies.GeodeticPoint;
import seakers.orekit.coverage.access.TimeIntervalArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class StupidRuleBasedPlanner {
    private ArrayList<SatelliteAction> results;
    private boolean downlinkEnabled;
    private boolean crosslinkEnabled;
    private ArrayList<Observation> sortedObservations;
    private TimeIntervalArray downlinks;
    private Map<String, TimeIntervalArray> crosslinks;
    private Map<String,String> priorityInfo;
    private Map<GeodeticPoint,Double> rewardGrid;
    private Map<String,String> settings;

    public StupidRuleBasedPlanner(ArrayList<Observation> sortedObservations, TimeIntervalArray downlinks, Map<GeodeticPoint,Double> rewardGrid, SatelliteState initialState, Map<String,String> priorityInfo, Map<String, String> settings) {
        this.sortedObservations = sortedObservations;
        this.downlinks = downlinks;
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
        System.out.println(s.getDataStored());
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

    public SatelliteAction selectAction(SatelliteState s) {
        ArrayList<SatelliteAction> possibleActions = getActionSpace(s);
        SatelliteAction bestAction = null;
        System.out.println(s.getBatteryCharge());
        if(s.getBatteryCharge() < 15) {
            bestAction = new SatelliteAction(s.getT(),s.getT()+60.0,null,"charge");
            return bestAction;
        }
        for (SatelliteAction a : possibleActions) {
            bestAction = a;
            break;
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


