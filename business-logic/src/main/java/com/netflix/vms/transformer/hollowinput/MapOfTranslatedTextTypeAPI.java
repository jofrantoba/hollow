package com.netflix.vms.transformer.hollowinput;

import com.netflix.hollow.read.customapi.HollowMapTypeAPI;

import com.netflix.hollow.read.dataaccess.HollowMapTypeDataAccess;
import com.netflix.hollow.objects.delegate.HollowMapLookupDelegate;

@SuppressWarnings("all")
public class MapOfTranslatedTextTypeAPI extends HollowMapTypeAPI {

    private final HollowMapLookupDelegate delegateLookupImpl;

    MapOfTranslatedTextTypeAPI(VMSHollowInputAPI api, HollowMapTypeDataAccess dataAccess) {
        super(api, dataAccess);
        this.delegateLookupImpl = new HollowMapLookupDelegate(this);
    }

    public MapKeyTypeAPI getKeyAPI() {
        return getAPI().getMapKeyTypeAPI();
    }

    public TranslatedTextValueTypeAPI getValueAPI() {
        return getAPI().getTranslatedTextValueTypeAPI();
    }

    public HollowMapLookupDelegate getDelegateLookupImpl() {
        return delegateLookupImpl;
    }

    public VMSHollowInputAPI getAPI() {
        return (VMSHollowInputAPI)api;
    }

}