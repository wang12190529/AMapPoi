package com.civitasv.spider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.civitasv.spider.model.po.JobPo;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author zhanghang
 * @since 2022-04-06 09:08:52
 */
public interface JobMapper extends BaseMapper<JobPo> {
    @Update("TRUNCATE TABLE tmp_truncate_table")
    void truncate();
}
