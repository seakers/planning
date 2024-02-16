package seakers.planning;

import org.orekit.bodies.GeodeticPoint;
import org.orekit.frames.TopocentricFrame;
import org.orekit.time.AbsoluteDate;
import seakers.orekit.analysis.Record;
import seakers.orekit.coverage.access.TimeIntervalArray;
import seakers.orekit.object.Instrument;
import seakers.orekit.object.Satellite;

import java.util.*;

public class SMDPPlanner {
    private ArrayList<Observation> results;
    private Satellite satellite;
    private Collection<Record<String>> groundTrack;
    private double startTime;
    private double endTime;
    private Map<Double,Map<GeodeticPoint,Double>> covPointRewards;
    private Map<TopocentricFrame, TimeIntervalArray> sortedGPAccesses;
    private TimeIntervalArray downlinks;
    private AbsoluteDate startDate;
    private double gamma;
    private int dSolveInit;
    private Map<GeodeticPoint,Double> latestRewardGrid;
    private Map<Double, GeodeticPoint> sspMap;

    public SMDPPlanner(Satellite satellite, Map<TopocentricFrame, TimeIntervalArray> sortedGPAccesses, TimeIntervalArray downlinks, AbsoluteDate startDate, Map<Double,Map<GeodeticPoint,Double>> covPointRewards, Collection<Record<String>> groundTrack, double startTime, double endTime) {
        this.satellite = satellite; //System.out.println(satellite.getName());
        this.sortedGPAccesses = sortedGPAccesses;
        this.downlinks = downlinks;
        this.startDate = startDate;
        this.covPointRewards = covPointRewards;
        this.groundTrack = groundTrack;
        this.startTime = startTime;
        this.endTime = endTime;
        this.gamma = 0.75;
        this.dSolveInit = 2;
        Map<Double, GeodeticPoint> sspMap = new HashMap<>();
        for (Record<String> ind : groundTrack) {
            String rawString = ind.getValue();
            AbsoluteDate date = ind.getDate();
            String[] splitString = rawString.split(",");
            double latitude = Double.parseDouble(splitString[0]);
            double longitude = Double.parseDouble(splitString[1]);
            GeodeticPoint ssp = new GeodeticPoint(Math.toRadians(latitude),Math.toRadians(longitude),0);
            double elapsedTime = date.durationFrom(startDate);
            sspMap.put(elapsedTime,ssp);
        }
        this.sspMap = sspMap;
        ArrayList<GeodeticPoint> initialImages = new ArrayList<>();
        ArrayList<StateAction> stateActions = null;
        if (sortedGPAccesses.size() > dSolveInit){ //System.out.println(sortedGPAccesses.size());
            stateActions = forwardSearch(new SatelliteState(startTime,startTime,initialImages));
            ArrayList<Observation> observations = new ArrayList<>();
            for (StateAction stateAction : stateActions) {
                Observation newObs = new Observation(stateAction.getA().getLocation(),stateAction.getA().gettStart(),stateAction.getA().gettEnd(), stateAction.getA().getAngle(),stateAction.getA().getReward());
                observations.add(newObs);
            }
            results = observations;}
        else {results = new ArrayList<>();}
    }

    public ActionResult SelectAction(SatelliteState s, int dSolve){
        if (dSolve == 0) {
            return new ActionResult(null,0);
        }
        ActionResult res = new ActionResult(null,Double.NEGATIVE_INFINITY);
        SatelliteState sCopy = new SatelliteState(s.getT(),s.gettPrevious(),s.getImages(),s.getCurrentAngle());
        ArrayList<SatelliteAction> feasibleActions = getActionSpace(sCopy);
        for (int a = 0; a < feasibleActions.size(); a++) {
            double value = 0;
            value = rewardFunction(sCopy,feasibleActions.get(a),getIncidenceAngle(feasibleActions.get(a)));
            SatelliteState tempSatelliteState = transitionFunction(sCopy,feasibleActions.get(a));
            ActionResult tempRes = SelectAction(tempSatelliteState,dSolve-1);
            if(feasibleActions.get(a).gettStart()-sCopy.getT() < 0) {
                System.out.println("time travel");
            }
            value = value + Math.pow(gamma,feasibleActions.get(a).gettStart()-sCopy.getT())*tempRes.getV();
            if (value > res.getV()) {
                res = new ActionResult(feasibleActions.get(a),value);
            }
        }
        return res;
    }
    public ArrayList<StateAction> forwardSearch(SatelliteState initialState) {
        ArrayList<StateAction> resultList = new ArrayList<>();
        HashMap<GeodeticPoint, ArrayList<Instrument>> imageSensorMap = new HashMap<>();

        latestRewardGrid = covPointRewards.get(0.0);
        ActionResult initRes = SelectAction(initialState,dSolveInit);
        if (initRes.getA() == null) {
            System.out.println("no possible actions!");
            return resultList;
        }
        imageSensorMap.put(initRes.getA().getLocation(), satellite.getPayload());

        SatelliteState newSatelliteState = transitionFunction(initialState,initRes.getA());
        double value = initRes.getV();
        double initReward = unweightedRewardFunction(newSatelliteState,initRes.getA(),imageSensorMap,getIncidenceAngle(initRes.getA()));
        //double initReward = rewardFunction(newSatelliteState,initRes.getA(),Math.random());
        double totalScore = initReward;
        initRes.getA().setReward(initReward);
        resultList.add(new StateAction(initialState,initRes.getA()));
//        System.out.println(newSatelliteState.getT());
//        System.out.println(totalScore);
//        System.out.println(resultList.size());
//        System.out.println(newSatelliteState.getImages());
        while(value != Double.NEGATIVE_INFINITY) {
            ActionResult res = SelectAction(newSatelliteState,dSolveInit);
            if(res.getA()==null) {
                break;
            }

            imageSensorMap.put(res.getA().getLocation(), satellite.getPayload());
            double reward = unweightedRewardFunction(newSatelliteState,res.getA(),imageSensorMap,getIncidenceAngle(res.getA()));
            res.getA().setReward(reward);
            totalScore = totalScore + reward;
            newSatelliteState = transitionFunction(newSatelliteState,res.getA());
//            System.out.println(newSatelliteState.getT());
            resultList.add(new StateAction(newSatelliteState,res.getA()));
            value = res.getV();
//            System.out.println(totalScore);
//            System.out.println(resultList.size());
//            System.out.println(newSatelliteState.getImages());
        }
        return resultList;
    }

    public double rewardFunction(SatelliteState s, SatelliteAction a, double incidenceAngle){
        GeodeticPoint gp = a.getLocation();
        ArrayList<GeodeticPoint> newImageSet = s.getImages();
        double score = 0;
//        if(!newImageSet.contains(gp)) { // UNIQUE OBSERVATIONS
            score = latestRewardGrid.get(gp);
            score = score * (1 - incidenceAngle);
            score = score * Math.pow(gamma, a.gettStart() - s.getT());
//        }
        return score;
    }

    public double unweightedRewardFunction(SatelliteState s, SatelliteAction a, HashMap<GeodeticPoint, ArrayList<Instrument>> imageSensorMap, double incidenceAngle){
        GeodeticPoint gp = a.getLocation();

        // used for slewing only problem
        ArrayList<GeodeticPoint> newImageSet = s.getImages();
        double score = 0;
        score = latestRewardGrid.get(gp);
        score = score * (1 - incidenceAngle);

        // used for slewing and reward problem
//        ArrayList<Instrument> pastSensors = imageSensorMap.get(gp);
//        // System.out.println(gp); System.out.println(pastSensors);
//
//        double score = 0;
//        if (!pastSensors.contains(satellite.getPayload())) {
//            score = latestRewardGrid.get(gp);
//            score = score * (1 - incidenceAngle);
//        }

        return score;
    }

    public SatelliteState transitionFunction(SatelliteState s, SatelliteAction a) {
        double t = a.gettEnd();
        double tPrevious = s.getT();
        GeodeticPoint location = a.getLocation();
        ArrayList<GeodeticPoint> newImageSet = (ArrayList<GeodeticPoint>) s.getImages().clone();
        if(!s.getImages().contains(location)) {
            newImageSet.add(location);
        }
        SatelliteState newS = new SatelliteState(t,tPrevious,newImageSet,a.getAngle());
        return newS;
    }

    public double getIncidenceAngle(GeodeticPoint point, double riseTime, double setTime, AbsoluteDate startDate, Satellite satellite, Collection<Record<String>> groundTrack) {
        double[] times = linspace(riseTime,setTime,(int)(setTime-riseTime));
        if(times.length<2) {
            times = new double[]{riseTime, setTime};
        }

        double closestDist = 100000000000000000.0;
        for (double time : times) {
            double closestTime = 100 * 24 * 3600; // 100 days
            GeodeticPoint closestPoint = null;
            for (Double sspTime : sspMap.keySet()) {
                if (Math.abs(sspTime - time) < closestTime) {
                    closestTime = Math.abs(sspTime - time);
                    closestPoint = sspMap.get(sspTime);
                    double dist = Math.sqrt(Math.pow(LLAtoECI(closestPoint)[0] - LLAtoECI(point)[0], 2) + Math.pow(LLAtoECI(closestPoint)[1] - LLAtoECI(point)[1], 2) + Math.pow(LLAtoECI(closestPoint)[2] - LLAtoECI(point)[2], 2));
                    if (dist < closestDist) {
                        closestDist = dist;
                    }
                }
            }
        }
        return Math.atan2(closestDist,(satellite.getOrbit().getA()-6370000)/1000);
    }
    public ArrayList<SatelliteAction> getActionSpace(SatelliteState s) {
        double currentTime = s.getT();
        double allowableTime = 15;
        boolean satisfied = false;
        ArrayList<SatelliteAction> possibleActions = new ArrayList<>();
        while(!satisfied) {
            for (TopocentricFrame tf : sortedGPAccesses.keySet()) {
                double[] riseandsets = sortedGPAccesses.get(tf).getRiseAndSetTimesList();
                for (int j = 0; j < riseandsets.length; j = j + 2) {
                    if (currentTime < riseandsets[j] && riseandsets[j] < currentTime+allowableTime) {
                        double maxTorque = 0.1; // Nm
                        double inertia = 2.66; // kg-m2
                        //double newAngle = Math.random();
                        double newAngle = getIncidenceAngle(tf.getPoint(),riseandsets[j],riseandsets[j+1],startDate,satellite,groundTrack);
                        double slewTorque = 4*Math.abs(newAngle-s.getCurrentAngle())*inertia/Math.pow(Math.abs(currentTime-riseandsets[j+1]),2);
                        if(slewTorque > maxTorque) {
                            //System.out.println("Can't slew! Last angle: "+s.getCurrentAngle()+", new angle: "+newAngle+". Last time: "+currentTime+", new time: "+riseandsets[j+1]);
                            continue;
                        }
                        SatelliteAction action = new SatelliteAction(riseandsets[j], riseandsets[j + 1], tf.getPoint(), "", 0.0, newAngle);
                        possibleActions.add(action);
                    }
                }
            }
            if(possibleActions.size() < 5  && currentTime+allowableTime < endTime) {
                allowableTime = allowableTime + 15;
            } else {
                satisfied = true;
            }
        }
        return possibleActions;
    }
    public double getIncidenceAngle(SatelliteAction a) {
        GeodeticPoint point = a.getLocation();
        double setTime = a.gettEnd();
        double riseTime = a.gettStart();
        double[] times = linspace(riseTime,setTime,(int)(setTime-riseTime));
        if(times.length<2) {
            times = new double[]{riseTime, setTime};
        }

        double closestDist = 100000000000000000.0;
        for (double time : times) {
            double closestTime = 100 * 24 * 3600; // 100 days
            GeodeticPoint closestPoint = null;
            for (Double sspTime : sspMap.keySet()) {
                if (Math.abs(sspTime - time) < closestTime) {
                    closestTime = Math.abs(sspTime - time);
                    closestPoint = sspMap.get(sspTime);
                    double dist = Math.sqrt(Math.pow(LLAtoECI(closestPoint)[0] - LLAtoECI(point)[0], 2) + Math.pow(LLAtoECI(closestPoint)[1] - LLAtoECI(point)[1], 2) + Math.pow(LLAtoECI(closestPoint)[2] - LLAtoECI(point)[2], 2));
                    if (dist < closestDist) {
                        closestDist = dist;
                    }
                }
            }
        }
        return Math.atan2(closestDist,(satellite.getOrbit().getA()-6370000)/1000);
    }

    public double[] LLAtoECI(GeodeticPoint point) {
        double re = 6370;
        double x = re * Math.cos(point.getLatitude()) * Math.cos(point.getLongitude());
        double y = re * Math.cos(point.getLatitude()) * Math.sin(point.getLongitude());
        double z = re * Math.sin(point.getLatitude());
        double[] result = {x,y,z};
        return result;
    }

    public double[] linspace(double min, double max, int points) {
        double[] d = new double[points];
        for (int i = 0; i < points; i++){
            d[i] = min + i * (max - min) / (points - 1);
        }
        return d;
    }

    public ArrayList<Observation> getResults() {
        return results;
    }
}
