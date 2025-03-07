package io.split.engine.evaluator;

import io.split.client.dtos.ConditionType;
import io.split.client.exceptions.ChangeNumberExceptionWrapper;
import io.split.cache.SplitCache;
import io.split.engine.experiments.ParsedCondition;
import io.split.engine.experiments.ParsedSplit;
import io.split.engine.splitter.Splitter;
import io.split.grammar.Treatments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class EvaluatorImp implements Evaluator {


    private static final Logger _log = LoggerFactory.getLogger(EvaluatorImp.class);

    private final SplitCache _splitCache;

    public EvaluatorImp(SplitCache splitCache) {
        _splitCache = checkNotNull(splitCache);
    }

    @Override
    public TreatmentLabelAndChangeNumber evaluateFeature(String matchingKey, String bucketingKey, String split, Map<String, Object> attributes) {
        try {
            ParsedSplit parsedSplit = _splitCache.get(split);

            if (parsedSplit == null) {
                return new TreatmentLabelAndChangeNumber(Treatments.CONTROL, Labels.DEFINITION_NOT_FOUND);
            }

            return getTreatment(matchingKey, bucketingKey, parsedSplit, attributes);
        }
        catch (ChangeNumberExceptionWrapper e) {
            _log.error("Evaluator Exception", e.wrappedException());
            return new EvaluatorImp.TreatmentLabelAndChangeNumber(Treatments.CONTROL, Labels.EXCEPTION, e.changeNumber());
        } catch (Exception e) {
            _log.error("Evaluator Exception", e);
            return new EvaluatorImp.TreatmentLabelAndChangeNumber(Treatments.CONTROL, Labels.EXCEPTION);
        }
    }

    /**
     * @param matchingKey  MUST NOT be null
     * @param bucketingKey
     * @param parsedSplit  MUST NOT be null
     * @param attributes   MUST NOT be null
     * @return
     * @throws ChangeNumberExceptionWrapper
     */
    private TreatmentLabelAndChangeNumber getTreatment(String matchingKey, String bucketingKey, ParsedSplit parsedSplit, Map<String, Object> attributes) throws ChangeNumberExceptionWrapper {
        try {
            if (parsedSplit.killed()) {
                String config = parsedSplit.configurations() != null ? parsedSplit.configurations().get(parsedSplit.defaultTreatment()) : null;
                return new TreatmentLabelAndChangeNumber(parsedSplit.defaultTreatment(), Labels.KILLED, parsedSplit.changeNumber(), config);
            }

            /*
             * There are three parts to a single Split: 1) Whitelists 2) Traffic Allocation
             * 3) Rollout. The flag inRollout is there to understand when we move into the Rollout
             * section. This is because we need to make sure that the Traffic Allocation
             * computation happens after the whitelist but before the rollout.
             */
            boolean inRollout = false;

            String bk = (bucketingKey == null) ? matchingKey : bucketingKey;

            for (ParsedCondition parsedCondition : parsedSplit.parsedConditions()) {

                if (!inRollout && parsedCondition.conditionType() == ConditionType.ROLLOUT) {

                    if (parsedSplit.trafficAllocation() < 100) {
                        // if the traffic allocation is 100%, no need to do anything special.
                        int bucket = Splitter.getBucket(bk, parsedSplit.trafficAllocationSeed(), parsedSplit.algo());

                        if (bucket > parsedSplit.trafficAllocation()) {
                            // out of split
                            String config = parsedSplit.configurations() != null ? parsedSplit.configurations().get(parsedSplit.defaultTreatment()) : null;
                            return new TreatmentLabelAndChangeNumber(parsedSplit.defaultTreatment(), Labels.NOT_IN_SPLIT, parsedSplit.changeNumber(), config);
                        }

                    }
                    inRollout = true;
                }

                if (parsedCondition.matcher().match(matchingKey, bucketingKey, attributes, this)) {
                    String treatment = Splitter.getTreatment(bk, parsedSplit.seed(), parsedCondition.partitions(), parsedSplit.algo());
                    String config = parsedSplit.configurations() != null ? parsedSplit.configurations().get(treatment) : null;
                    return new TreatmentLabelAndChangeNumber(treatment, parsedCondition.label(), parsedSplit.changeNumber(), config);
                }
            }

            String config = parsedSplit.configurations() != null ? parsedSplit.configurations().get(parsedSplit.defaultTreatment()) : null;
            return new TreatmentLabelAndChangeNumber(parsedSplit.defaultTreatment(), Labels.DEFAULT_RULE, parsedSplit.changeNumber(), config);
        } catch (Exception e) {
            throw new ChangeNumberExceptionWrapper(e, parsedSplit.changeNumber());
        }
    }

    public static final class TreatmentLabelAndChangeNumber {
        public final String treatment;
        public final String label;
        public final Long changeNumber;
        public final String configurations;

        public TreatmentLabelAndChangeNumber(String treatment, String label) {
            this(treatment, label, null, null);
        }

        public TreatmentLabelAndChangeNumber(String treatment, String label, Long changeNumber) {
            this(treatment, label, changeNumber, null);
        }

        public TreatmentLabelAndChangeNumber(String treatment, String label, Long changeNumber, String configurations) {
            this.treatment = treatment;
            this.label = label;
            this.changeNumber = changeNumber;
            this.configurations = configurations;
        }
    }
}
