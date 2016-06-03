package com.netflix.vms.transformer.publish.workflow.circuitbreaker;

import static com.netflix.vms.transformer.common.TransformerLogger.LogTag.CircuitBreaker;

import com.netflix.vms.transformer.common.TransformerMetricRecorder.Metric;

import com.netflix.hollow.index.HollowPrimaryKeyIndex;
import com.netflix.hollow.read.engine.HollowReadStateEngine;
import com.netflix.vms.generated.notemplate.CompleteVideoHollow;
import com.netflix.vms.generated.notemplate.FloatHollow;
import com.netflix.vms.generated.notemplate.IntegerHollow;
import com.netflix.vms.generated.notemplate.MapOfIntegerToFloatHollow;
import com.netflix.vms.generated.notemplate.TopNVideoDataHollow;
import com.netflix.vms.generated.notemplate.VMSRawHollowAPI;
import com.netflix.vms.transformer.publish.workflow.PublishWorkflowContext;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public abstract class HollowPerCountryTopNVideoScoringCircuitBreaker extends HollowFixedBaseLineCountrySpecificCircuitBreaker {

	public HollowPerCountryTopNVideoScoringCircuitBreaker(PublishWorkflowContext ctx, long versionId) {
		super(ctx, versionId);
	}
	
    @Override
    protected final CircuitBreakerResults runCircuitBreaker(HollowReadStateEngine stateEngine) {
    	CircuitBreakerResults results = new CircuitBreakerResults();
    	
		VMSRawHollowAPI api = new VMSRawHollowAPI(stateEngine);
		
		HollowPrimaryKeyIndex idx = new HollowPrimaryKeyIndex(stateEngine, "CompleteVideo", "id.value", "country.id");
		
		for(TopNVideoDataHollow topn: api.getAllTopNVideoDataHollow()){
			
			MapOfIntegerToFloatHollow videoViewHrsHollow = topn._getVideoViewHrs1Day();
			
			float countryViewHrs1Day = topn._getCountryViewHrs1Day();
			
			String countryId = topn._getCountryId();
			
			if(videoViewHrsHollow != null && Float.compare(0, countryViewHrs1Day)!=0){
			
				float missingViewShare = 0f;
				Set<Integer> missingVideoIDs = new HashSet<>();
				for(Entry<IntegerHollow, FloatHollow> entryH: videoViewHrsHollow.entrySet()){
					
					int videoId = entryH.getKey()._getVal();
					int compVideoOrdinal = idx.getMatchingOrdinal(videoId, countryId);
					
					int videoScore = getVideoScore(api.getCompleteVideoHollow(compVideoOrdinal));
					if(videoScore < 1){
						float videoViewShare = (entryH.getValue()._getVal()/countryViewHrs1Day)*100;
						missingViewShare += videoViewShare;
						missingVideoIDs.add(videoId);
					}
				}
				results.addResult(compareMetric(countryId, missingViewShare));
				if(missingVideoIDs.size() > 0)
					ctx.getLogger().warn(CircuitBreaker, getRuleName()+": "+countryId+": Unavailable TopN video IDs: "+missingVideoIDs);
				
				ctx.getMetricRecorder().recordMetric(Metric.TopNMissingViewShare, missingViewShare);
			}
		}
        return results;
    }

	protected abstract int getVideoScore(CompleteVideoHollow cv);
}
