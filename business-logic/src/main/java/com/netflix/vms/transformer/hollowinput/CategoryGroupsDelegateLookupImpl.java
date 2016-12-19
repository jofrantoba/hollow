package com.netflix.vms.transformer.hollowinput;

import com.netflix.hollow.objects.delegate.HollowObjectAbstractDelegate;
import com.netflix.hollow.read.dataaccess.HollowObjectTypeDataAccess;
import com.netflix.hollow.HollowObjectSchema;

@SuppressWarnings("all")
public class CategoryGroupsDelegateLookupImpl extends HollowObjectAbstractDelegate implements CategoryGroupsDelegate {

    private final CategoryGroupsTypeAPI typeAPI;

    public CategoryGroupsDelegateLookupImpl(CategoryGroupsTypeAPI typeAPI) {
        this.typeAPI = typeAPI;
    }

    public long getCategoryGroupId(int ordinal) {
        return typeAPI.getCategoryGroupId(ordinal);
    }

    public Long getCategoryGroupIdBoxed(int ordinal) {
        return typeAPI.getCategoryGroupIdBoxed(ordinal);
    }

    public int getCategoryGroupNameOrdinal(int ordinal) {
        return typeAPI.getCategoryGroupNameOrdinal(ordinal);
    }

    public CategoryGroupsTypeAPI getTypeAPI() {
        return typeAPI;
    }

    @Override
    public HollowObjectSchema getSchema() {
        return typeAPI.getTypeDataAccess().getSchema();
    }

    @Override
    public HollowObjectTypeDataAccess getTypeDataAccess() {
        return typeAPI.getTypeDataAccess();
    }

}