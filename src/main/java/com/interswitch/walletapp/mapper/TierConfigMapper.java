package com.interswitch.walletapp.mapper;

import com.interswitch.walletapp.entities.TierConfig;
import com.interswitch.walletapp.models.response.TierConfigResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TierConfigMapper {
    TierConfigResponse map(TierConfig tierConfig);

    List<TierConfigResponse> map(List<TierConfig> tierConfigs);
}