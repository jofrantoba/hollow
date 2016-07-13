package com.netflix.vms.transformer.publish.workflow;

import static com.netflix.vms.transformer.common.io.TransformerLogTag.BlobChecksum;
import static com.netflix.vms.transformer.common.io.TransformerLogTag.CircuitBreaker;

import java.io.File;
import java.io.IOException;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.netflix.hollow.read.engine.HollowBlobReader;
import com.netflix.hollow.read.engine.HollowReadStateEngine;
import com.netflix.hollow.read.engine.PopulatedOrdinalListener;
import com.netflix.hollow.read.engine.object.HollowObjectTypeReadState;
import com.netflix.hollow.util.HashCodes;
import com.netflix.hollow.util.HollowChecksum;
import com.netflix.vms.generated.notemplate.ISOCountryHollow;
import com.netflix.vms.generated.notemplate.PackageDataHollow;
import com.netflix.vms.generated.notemplate.TopNVideoDataHollow;
import com.netflix.vms.generated.notemplate.VMSRawHollowAPI;
import com.netflix.vms.generated.notemplate.VideoHollow;
import com.netflix.vms.transformer.common.TransformerContext;

public class HollowBlobDataProvider {
    /* dependencies */
    private final TransformerContext ctx;

    /* fields */
    private HollowReadStateEngine hollowReadStateEngine;
    private HollowBlobReader hollowBlobReader;

    public HollowBlobDataProvider(TransformerContext ctx) {
        this.ctx = ctx;
        this.hollowReadStateEngine = new HollowReadStateEngine(true);
        this.hollowBlobReader = new HollowBlobReader(hollowReadStateEngine);
    }
    
    public void notifyRestoredStateEngine(HollowReadStateEngine restoredState) {
        this.hollowReadStateEngine = restoredState;
        this.hollowBlobReader = new HollowBlobReader(restoredState);
    }

    public void readSnapshot(File snapshotFile) throws IOException {
        ctx.getLogger().info(CircuitBreaker, "Reading Snapshot blob {}", snapshotFile.getName());
        hollowReadStateEngine = new HollowReadStateEngine(true);
        hollowBlobReader = new HollowBlobReader(hollowReadStateEngine);
        hollowBlobReader.readSnapshot(ctx.files().newBlobInputStream(snapshotFile));
    }

    public void readDelta(File deltaFile) throws IOException {
        ctx.getLogger().info(CircuitBreaker, "Reading Delta blob {}", deltaFile.getName());
        hollowBlobReader.applyDelta(ctx.files().newBlobInputStream(deltaFile));
    }

    public HollowReadStateEngine getStateEngine() {
        return hollowReadStateEngine;
    }

    public int countItems(String typeName) {
        return hollowReadStateEngine.getTypeState(typeName).getListener(PopulatedOrdinalListener.class).getPopulatedOrdinals().cardinality();
    }

	public void updateData(File snapshotFile, File deltaFile, File reverseDeltaFile) throws IOException {
		if (deltaFile.exists() && snapshotFile.exists()) {
		    validateChecksums(snapshotFile, deltaFile, reverseDeltaFile);
        } else if (snapshotFile.exists()) {
            readSnapshot(snapshotFile);
        } else {
            throw new RuntimeException("Neither snapshot, nor delta file found. Failing update.");
        }
    }

    private void validateChecksums(File snapshotFile, File deltaFile, File reverseDeltaFile) throws IOException {
        HollowReadStateEngine anotherStateEngine = new HollowReadStateEngine();
        HollowBlobReader anotherReader = new HollowBlobReader(anotherStateEngine);
        anotherReader.readSnapshot(ctx.files().newBlobInputStream(snapshotFile));
        
        HollowChecksum initialChecksumBeforeDelta = null;
        if(reverseDeltaFile.exists())
            initialChecksumBeforeDelta = HollowChecksum.forStateEngineWithCommonSchemas(hollowReadStateEngine, anotherStateEngine);
        
        readDelta(deltaFile);

        HollowChecksum deltaChecksum = HollowChecksum.forStateEngineWithCommonSchemas(hollowReadStateEngine, anotherStateEngine);
        HollowChecksum snapshotChecksum = HollowChecksum.forStateEngineWithCommonSchemas(anotherStateEngine, hollowReadStateEngine);

        ctx.getLogger().info(BlobChecksum, "DELTA STATE CHECKSUM: {}", deltaChecksum);
        ctx.getLogger().info(BlobChecksum, "SNAPSHOT STATE CHECKSUM: {}", snapshotChecksum);

        if(!deltaChecksum.equals(snapshotChecksum))
            throw new RuntimeException("DELTA CHECKSUM VALIDATION FAILURE!");

        if(reverseDeltaFile.exists()) {
            anotherReader.applyDelta(ctx.files().newBlobInputStream(reverseDeltaFile));
            HollowChecksum reverseDeltaChecksum = HollowChecksum.forStateEngineWithCommonSchemas(anotherStateEngine, hollowReadStateEngine);

            ctx.getLogger().info(BlobChecksum, "INITIAL STATE CHECKSUM: {}", initialChecksumBeforeDelta);
            ctx.getLogger().info(BlobChecksum, "REVERSE DELTA STATE CHECKSUM: {}", reverseDeltaChecksum);

            if(!initialChecksumBeforeDelta.equals(reverseDeltaChecksum))
                throw new RuntimeException("REVERSE DELTA CHECKSUM VALIDATION FAILURE!");
        }
    }

    public Map<String, Set<Integer>> changedVideoCountryKeysBasedOnCompleteVideos() {
        HollowObjectTypeReadState typeState = (HollowObjectTypeReadState) hollowReadStateEngine.getTypeState("CompleteVideo");
        PopulatedOrdinalListener completeVideoListener = typeState.getListener(PopulatedOrdinalListener.class);
        BitSet modifiedCompleteVideos = new BitSet(completeVideoListener.getPopulatedOrdinals().length());
        modifiedCompleteVideos.or(completeVideoListener.getPopulatedOrdinals());
        modifiedCompleteVideos.xor(completeVideoListener.getPreviousOrdinals());

        if(modifiedCompleteVideos.cardinality() == 0 || modifiedCompleteVideos.cardinality() == completeVideoListener.getPopulatedOrdinals().cardinality())
            return Collections.emptyMap();

        int countryOrdinalFieldIndex = typeState.getSchema().getPosition("country");
        int videoIdFieldIndex = typeState.getSchema().getPosition("id");

        HollowObjectTypeReadState countryState = (HollowObjectTypeReadState) hollowReadStateEngine.getTypeState("ISOCountry");
        int countryIdFieldIndex = countryState.getSchema().getPosition("id");
        String countryIds[] = new String[countryState.maxOrdinal() + 1];

        Map<String, Set<Integer>> modifiedIds = new HashMap<>();

        int ordinal = modifiedCompleteVideos.nextSetBit(0);
        while(ordinal != -1) {
            int countryOrdinal = typeState.readOrdinal(ordinal, countryOrdinalFieldIndex);
            int videoId = typeState.readInt(ordinal, videoIdFieldIndex);
            String countryId = countryIds[countryOrdinal];
            if(countryId == null) {
                countryId = countryState.readString(countryOrdinal, countryIdFieldIndex);
                countryIds[countryOrdinal] = countryId;
            }

            Set<Integer> set = modifiedIds.get(countryId);
            if(set == null)
                set = new HashSet<>();
            set.add(videoId);
            modifiedIds.put(countryId, set);

            ordinal = modifiedCompleteVideos.nextSetBit(ordinal + 1);
        }

        return modifiedIds;
    }

	public Map<String, Set<Integer>> changedVideoCountryKeysBasedOnPackages() {
		HollowObjectTypeReadState typeState = (HollowObjectTypeReadState) hollowReadStateEngine.getTypeState("PackageData");
		PopulatedOrdinalListener packageListener = typeState.getListener(PopulatedOrdinalListener.class);
		BitSet modifiedPackages = new BitSet(packageListener.getPopulatedOrdinals().length());
		modifiedPackages.or(packageListener.getPopulatedOrdinals());
		modifiedPackages.xor(packageListener.getPreviousOrdinals());

		if (modifiedPackages.cardinality() == 0|| modifiedPackages.cardinality() == packageListener.getPopulatedOrdinals().cardinality())
			return Collections.emptyMap();

		Map<String, Set<Integer>> modifiedPackageVideoIds = new HashMap<>();
		VMSRawHollowAPI api = new VMSRawHollowAPI(hollowReadStateEngine);

		int ordinal = modifiedPackages.nextSetBit(0);
		while (ordinal != -1) {
			PackageDataHollow packageData = (PackageDataHollow) api.getPackageDataHollow(ordinal);
			Set<ISOCountryHollow> deployCountries = packageData._getAllDeployableCountries();
			VideoHollow video = packageData._getVideo();

			if (deployCountries == null || video == null)
				continue;

			Iterator<ISOCountryHollow> iterator = deployCountries.iterator();
			while (iterator.hasNext()) {
				String countryId = iterator.next()._getId();
				Set<Integer> modIdsForCountry = modifiedPackageVideoIds.get(countryId);
				if (modIdsForCountry == null) {
					modIdsForCountry = new HashSet<Integer>();
					modifiedPackageVideoIds.put(countryId, modIdsForCountry);
				}
				modIdsForCountry.add(video._getValue());
			}
			ordinal = modifiedPackages.nextSetBit(ordinal + 1);
		}
		return modifiedPackageVideoIds;
    }

    public long getLoadedVersion(){
        return Long.parseLong(hollowReadStateEngine.getHeaderTag("dataVersion"));
    }

	public static class VideoCountryKey {
		private final String country;
	    private final int videoId;

	    public VideoCountryKey(String country, int videoId) {
	        this.country = country;
	        this.videoId = videoId;
	    }

        public String getCountry() {
            return country;
        }

	    public int getVideoId() {
	        return videoId;
	    }

	    @Override
		public int hashCode() {
	        return HashCodes.hashInt(country.hashCode()) ^ HashCodes.hashInt(videoId);
	    }

	    @Override
		public boolean equals(Object other) {
	        if(other instanceof VideoCountryKey) {
	            return ((VideoCountryKey) other).getCountry().equals(country) && ((VideoCountryKey) other).getVideoId() == videoId;
	        }
	        return false;
	    }
	    @Override
	    public String toString() {
          return "VideoCountryKey [country=" + country + ", videoId="
              + videoId + "]";
	    }

	    public String toShortString() {
          return "["+country + ", "+ videoId + "]";
	    }
	}

    public Map<String, TopNVideoDataHollow> getTopNData() {
		// Read from blob
	    Map <String, TopNVideoDataHollow> result = new HashMap<>();
	    
		VMSRawHollowAPI api = new VMSRawHollowAPI(hollowReadStateEngine);
		
		for(TopNVideoDataHollow topn: api.getAllTopNVideoDataHollow()){
			
			String countryId = topn._getCountryId();
			
			result.put(countryId, topn);
			
		}
		return result;
    }

}
