package com.zhoushuo.eaqb.distributed.id.generator.biz.core;


import com.zhoushuo.eaqb.distributed.id.generator.biz.core.common.Result;

public interface IDGen {
    Result get(String key);
    boolean init();
}
