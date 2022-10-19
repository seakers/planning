package seakers.planning;

public class Reward {
    private double reward;
    private EventObservation obs;
    public Reward(double reward, EventObservation obs) {
        this.reward = reward;
        this.obs = obs;
    }
    public void setReward(double reward) {
        this.reward = reward;
    }
    public double getReward(double time) {
        if (time < obs.getObsTime()+(3600.0*8.0) && time > obs.getObsTime()) {
            return reward;
        } else {
            return 1.0;
        }
    }
    public EventObservation getObs() { return obs; }
}
