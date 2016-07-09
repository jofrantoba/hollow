package com.netflix.vms.transformer.modules.artwork;

import static com.netflix.vms.transformer.common.io.TransformerLogTag.*;

import com.netflix.hollow.write.objectmapper.HollowObjectMapper;
import com.netflix.vms.transformer.common.TransformerContext;
import com.netflix.vms.transformer.hollowinput.ArtworkAttributesHollow;
import com.netflix.vms.transformer.hollowinput.ArtworkDerivativeListHollow;
import com.netflix.vms.transformer.hollowinput.ArtworkLocaleHollow;
import com.netflix.vms.transformer.hollowinput.ArtworkLocaleListHollow;
import com.netflix.vms.transformer.hollowinput.PersonArtworkHollow;
import com.netflix.vms.transformer.hollowinput.VMSHollowInputAPI;
import com.netflix.vms.transformer.hollowoutput.Artwork;
import com.netflix.vms.transformer.hollowoutput.PersonImages;
import com.netflix.vms.transformer.index.VMSTransformerIndexer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PersonImagesModule extends ArtWorkModule{

    public PersonImagesModule(VMSHollowInputAPI api, TransformerContext ctx, HollowObjectMapper mapper, VMSTransformerIndexer indexer) {
        super("Person", api, ctx, mapper, indexer);
    }

    @Override
    public void transform() {
    	/// short-circuit Fastlane
    	if(ctx.getFastlaneIds() != null)
    		return;
    	
        Map<Integer, Set<Artwork>> artMap = new HashMap<>();
        for(PersonArtworkHollow artworkHollowInput : api.getAllPersonArtworkHollow()) {
            ArtworkLocaleListHollow locales = artworkHollowInput._getLocales();
            int entityId = (int) artworkHollowInput._getPersonId();
            Set<ArtworkLocaleHollow> localeSet = getLocalTerritories(locales);
            if(localeSet.isEmpty()) {
                ctx.getLogger().error(MissingLocaleForArtwork, "Missing artwork locale for {} with id={}; data will be dropped.", entityType, entityId);
                continue;
            }

            String sourceFileId = artworkHollowInput._getSourceFileId()._getValue();
            int ordinalPriority = (int) artworkHollowInput._getOrdinalPriority();
            int seqNum = (int) artworkHollowInput._getSeqNum();
            ArtworkAttributesHollow attributes = artworkHollowInput._getAttributes();
            ArtworkDerivativeListHollow derivatives = artworkHollowInput._getDerivatives();
            Set<Artwork> artworkSet = getArtworkSet(entityId, artMap);

            transformArtworks(entityId, sourceFileId, ordinalPriority, seqNum, attributes, derivatives, localeSet, artworkSet);
        }

        for (Map.Entry<Integer, Set<Artwork>> entry : artMap.entrySet()) {
            Integer id = entry.getKey();
            PersonImages images = new PersonImages();
            images.id = id;
            images.artworks = createArtworkByTypeMap(entry.getValue());
            mapper.addObject(images);
        }
    }
}
