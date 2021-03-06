package com.rw.finance.admin.service.impl;

import com.rw.finance.admin.dao.*;
import com.rw.finance.admin.model.CountAdminIndexModel;
import com.rw.finance.admin.model.CountAgentIndexModel;
import com.rw.finance.admin.service.PlatformCountService;
import com.rw.finance.common.entity.AgentInfo;
import com.rw.finance.common.entity.MemberInfo;
import com.rw.finance.common.utils.MathUtils;
import org.apache.tools.ant.util.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.Calendar;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 统计接口实现
 */
@Service
public class PlatformCountServiceImpl implements PlatformCountService {
    @PersistenceContext
    private EntityManager em;

    @Autowired
    private AgentInfoDao agentInfoDao;

    @Autowired
    private AgentProfitDao agentProfitDao;

    @Autowired
    private MemberInfoDao memberInfoDao;

    @Autowired
    private OrderInfoDao orderInfoDao;

    @Autowired
    private ActvcodeInfoDao actvcodeInfoDao;

    @Override
    public CountAgentIndexModel getAgentIndexTotal(Long agentid) {
        // 代理信息
        AgentInfo agentInfo = this.agentInfoDao.findOne(agentid);
        if (agentInfo == null) {
            return null;
        }

        // 统计信息
        CountAgentIndexModel model = new CountAgentIndexModel();
        if (agentInfo.getAgentlevel() > 0) {
            model.setMembercount(this.memberInfoDao.countByAgentid(agentInfo.getAgentid()));
            model.setAgentcount(this.agentInfoDao.countByParentidAndAgentlevelGreaterThan(agentInfo.getAgentid(), 0));

            List<Long> longs = null;
            List<MemberInfo> list = memberInfoDao.findByAgentid(agentInfo.getAgentid());
            if (list != null && list.size() > 0) {
                longs = list.stream().filter(s -> s.getMemberid() != null).map(s -> s.getMemberid())
                        .collect(Collectors.toList());
            }

            model.setProfitamount(agentProfitDao.sumByAgentid(agentInfo.getAgentid()));
            model.setTradeamount(orderInfoDao.sumByStatusAndAgentid(1, agentInfo.getAgentid(), longs));
            model.setActivecount(this.actvcodeInfoDao.countBySalestatusAndUsestatusAndAgentid(1, 1, agentInfo
                    .getAgentid()));
        } else {
            model.setMembercount(this.memberInfoDao.count());
            model.setAgentcount(this.agentInfoDao.countByAgentlevelGreaterThan(0));
            model.setProfitamount(agentProfitDao.sum());
            model.setTradeamount(orderInfoDao.sumByStatus(1));
            model.setActivecount(this.actvcodeInfoDao.countBySalestatusAndUsestatus(1, 1));
        }

        return model;
    }

    @Override
    public CountAdminIndexModel getAdminIndexTotal() {
        // 统计结果
        CountAdminIndexModel model = new CountAdminIndexModel();

        // 统计会员
        String sql = "SELECT is_real,level,COUNT(0) FROM member_info GROUP BY is_real,level";

        // 统计结果
        Query dataQuery = em.createNativeQuery(sql);
        List<Object[]> listData = dataQuery.getResultList();
        for (Object[] item : listData) {
            // 数量
            int count = Integer.parseInt(item[2].toString());

            // 统计总数
            model.setTotalusers(model.getTotalusers() + count);

            // 是否激活
            int isreal = Integer.parseInt(item[0].toString());
            if (isreal == 1) {
                model.setActiveusers(model.getActiveusers() + count);

                // 会员等级
                int level = Integer.parseInt(item[1].toString());
                switch (level) {
                    case 0:
                        model.setCoppersers(model.getCoppersers() + count);
                        break;
                    case 1:
                        model.setSilverusers(model.getSilverusers() + count);
                        break;
                    case 2:
                        model.setGoldusers(model.getGoldusers() + count);
                        break;
                }
            }
        }

        // 统计交易与收益
        sql = "SELECT tradetype,SUM(tradeamount),SUM(companyprofittotal) FROM order_count GROUP BY tradetype";

        // 统计结果
        dataQuery = em.createNativeQuery(sql);
        listData = dataQuery.getResultList();
        for (Object[] item : listData) {
            // 交易总额
            double tradeamount = Double.parseDouble(item[1].toString());
            model.setTotaltrade(MathUtils.add(model.getTotaltrade(), tradeamount));

            // 平台收益
            double companyprofit = Double.parseDouble(item[2].toString());
            model.setTotalprofit(MathUtils.add(model.getTotalprofit(), companyprofit));

            // 交易类型
            switch (item[0].toString()) {
                case "RepayTask":
                    model.setTotaltasktrade(MathUtils.add(model.getTotaltasktrade(), tradeamount));
                    model.setTotalrepayprofit(MathUtils.add(model.getTotalrepayprofit(), companyprofit));
                    break;
                case "MemberBorrow":
                    model.setTotalfasttrade(MathUtils.add(model.getTotalfasttrade(), tradeamount));
                    model.setTotalreciveprofit(MathUtils.add(model.getTotalreciveprofit(), companyprofit));
                    break;
                case "MemberActive":
                case "MemberCash":
                case "AgentCash":
                case "MemberCard":
                    model.setTotalfasttrade(MathUtils.add(model.getTotalfasttrade(), tradeamount));
                    break;
                case "ActvcodeSale":
                    model.setTotalfasttrade(MathUtils.add(model.getTotalfasttrade(), tradeamount));
                    model.setTotalactprofit(MathUtils.add(model.getTotalactprofit(), companyprofit));
                    break;
            }
        }

        // 统计代理数量
        sql = "SELECT COUNT(0) FROM agent_info WHERE agent_level > 0";

        // 统计结果
        dataQuery = em.createNativeQuery(sql);
        listData = dataQuery.getResultList();
        for (Object item : listData) {
            model.setTotalagents(Integer.parseInt(item.toString()));
        }

        // 统计剩余激活码
        sql = "SELECT COUNT(0) FROM actvcode_info WHERE use_status <> 2";

        // 统计结果
        dataQuery = em.createNativeQuery(sql);
        listData = dataQuery.getResultList();
        for (Object item : listData) {
            model.setSurplusactcode(Integer.parseInt(item.toString()));
        }

        // 结束时间
        Calendar now = Calendar.getInstance();
        String end = DateUtils.format(now.getTime(), "yyyy-MM-dd 23:59:59");

        // 今天
        String today = DateUtils.format(now.getTime(), "yyyy-MM-dd");

        // 开始时间
        now.add(Calendar.DAY_OF_MONTH, -1);
        String start = DateUtils.format(now.getTime(), "yyyy-MM-dd 00:00:00");

        // 昨天
        String yesterday = DateUtils.format(now.getTime(), "yyyy-MM-dd");

        // 统计用户增量
        sql = "SELECT DATE_FORMAT(register_time,\"%Y-%m-%d\"),COUNT(0) FROM member_info WHERE register_time >= '" +
                start + "' AND register_time <= '" + end + "' GROUP BY DATE_FORMAT(register_time, \"%Y-%m-%d\")";

        // 统计结果
        dataQuery = em.createNativeQuery(sql);
        listData = dataQuery.getResultList();
        for (Object[] item : listData) {
            String date = item[0].toString();
            if (date.equals(today)) {
                model.setTodayaddusers(Integer.parseInt(item[1].toString()));
            }

            if (date.equals(yesterday)) {
                model.setLastaddusers(Integer.parseInt(item[1].toString()));
            }
        }

        // 统计交易与收益
        sql = "SELECT tradetype,SUM(tradeamount),SUM(companyprofittotal),DATE_FORMAT(createtime,\"%Y-%m-%d\") FROM " +
                "order_count WHERE createtime >= '" + start + "' AND createtime <= '" + end + "' GROUP BY " +
                "tradetype,DATE_FORMAT(createtime, \"%Y-%m-%d\") ORDER BY createtime;";

        // 统计结果
        dataQuery = em.createNativeQuery(sql);
        listData = dataQuery.getResultList();
        for (Object[] item : listData) {
            // 交易总额
            double tradeamount = Double.parseDouble(item[1].toString());

            // 平台收益
            double companyprofit = Double.parseDouble(item[2].toString());

            // 交易日期
            String date = item[3].toString();
            if (date.equals(today)) {
                model.setTodaytrade(MathUtils.add(model.getTodaytrade(), tradeamount));
                model.setTodayprofit(MathUtils.add(model.getTodayprofit(), companyprofit));

                // 交易类型
                switch (item[0].toString()) {
                    case "RepayTask":
                        model.setTodaytasktrade(MathUtils.add(model.getTodaytasktrade(), tradeamount));
                        model.setTodayrepayprofit(MathUtils.add(model.getTodayrepayprofit(), companyprofit));
                        break;
                    case "MemberBorrow":
                        model.setTodayfasttrade(MathUtils.add(model.getTodayfasttrade(), tradeamount));
                        model.setTodayreciveprofit(MathUtils.add(model.getTodayreciveprofit(), companyprofit));
                        break;
                    case "MemberActive":
                    case "MemberCash":
                    case "AgentCash":
                    case "MemberCard":
                        model.setTodayfasttrade(MathUtils.add(model.getTodayfasttrade(), tradeamount));
                        break;
                    case "ActvcodeSale":
                        model.setTodayfasttrade(MathUtils.add(model.getTodayfasttrade(), tradeamount));
                        model.setTodayactprofit(MathUtils.add(model.getTodayactprofit(), companyprofit));
                        break;
                }
            }

            if (date.equals(yesterday)) {
                model.setLasttrade(MathUtils.add(model.getLasttrade(), tradeamount));
                model.setLastprofit(MathUtils.add(model.getLastprofit(), companyprofit));

                // 交易类型
                switch (item[0].toString()) {
                    case "RepayTask":
                        model.setLasttasktrade(MathUtils.add(model.getLasttasktrade(), tradeamount));
                        model.setLastrepayprofit(MathUtils.add(model.getLastrepayprofit(), companyprofit));
                        break;
                    case "MemberBorrow":
                        model.setLastfasttrade(MathUtils.add(model.getLastfasttrade(), tradeamount));
                        model.setLastreciveprofit(MathUtils.add(model.getLastreciveprofit(), companyprofit));
                        break;
                    case "MemberActive":
                    case "MemberCash":
                    case "AgentCash":
                    case "MemberCard":
                        model.setLastfasttrade(MathUtils.add(model.getLastfasttrade(), tradeamount));
                        break;
                    case "ActvcodeSale":
                        model.setLastfasttrade(MathUtils.add(model.getLastfasttrade(), tradeamount));
                        model.setLastactprofit(MathUtils.add(model.getLastactprofit(), companyprofit));
                        break;
                }
            }
        }

        return model;
    }
}
