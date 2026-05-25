package modules.prediction.strategies.prediction;

import modules.music.structures.Track;
import modules.prediction.strategies.core.PredictionStrategy;
import modules.prediction.structures.PredictionFactor;
import modules.userContext.services.UserContextService;
import modules.userContext.structures.MacroCurve;

import java.time.LocalTime;
import java.util.Map;

public class MacroCurveStrategy implements PredictionStrategy {
    @Override
    public double getWeight() {
        return 0.7;
    }

    @Override
    public PredictionFactor calculate(Track currentTrack) {
        var context = UserContextService.getInstance().getCurrentContext();
        LocalTime currentTime = context.currentTime();
        Map<String, MacroCurve> curves = context.attributeCurves();

        double targetEnergy = currentTrack.energy();
        double targetBpm = currentTrack.bpm();

        if (curves != null) {
            MacroCurve energyCurve = curves.get("energy");
            if (energyCurve != null) {
                targetEnergy = energyCurve.getTargetValueAt(currentTime);
            }

            MacroCurve bpmCurve = curves.get("bpm");
            if (bpmCurve != null) {
                targetBpm = bpmCurve.getTargetValueAt(currentTime);
            }
        }

        double energyDivisor = Math.max(0.01, currentTrack.energy());
        double bpmDivisor = Math.max(0.01, currentTrack.bpm());
        double energyFactor = targetEnergy / energyDivisor;
        double bpmFactor = targetBpm / bpmDivisor;

        return new PredictionFactor(energyFactor, bpmFactor);
    }
}
