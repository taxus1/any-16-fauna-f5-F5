package com.somepro.infrastructure.persistence.sample.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.somepro.infrastructure.persistence.base.BasePO;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * t_sample_test 表的持久化对象（PO，基础设施层）。
 *
 * 只描述「表长什么样」。表按现状用：结果回填走 UPDATE，检测时刻写 tested_at 列，
 * 审计列 update_time 由 MetaObjectHandler 自动刷新。
 */
@Getter
@Setter
@TableName("t_sample_test")
public class SampleTestPO extends BasePO {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("sample_no")
    private String sampleNo;

    @TableField("report_id")
    private Long reportId;

    @TableField("sample_type")
    private String sampleType;

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("lab_name")
    private String labName;

    @TableField("test_item")
    private String testItem;

    @TableField("result")
    private String result;

    @TableField("tested_at")
    private LocalDateTime testedAt;
}
