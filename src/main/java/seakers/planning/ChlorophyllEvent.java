package seakers.planning;

import org.orekit.bodies.GeodeticPoint;

import java.util.ArrayList;

public class ChlorophyllEvent {
    private GeodeticPoint location;
    private double startTime;
    private double endTime;
    private double chlLimit;
    private double currentChl;
    private ArrayList<String> eventLog;
    public ChlorophyllEvent(GeodeticPoint location, double startTime, double endTime, double chlLimit, double currentChl) {
        this.location = location;
        this.startTime = startTime;
        this.endTime = endTime;
        this.chlLimit = chlLimit;
        this.currentChl = currentChl;
        this.eventLog = new ArrayList<>();
    }

    public void addToEventLog(String inputString) {
        eventLog.add(inputString);
    }

    public double getEndTime() { return endTime; }

    public ArrayList<String> getEventLog() { return eventLog; }
}
