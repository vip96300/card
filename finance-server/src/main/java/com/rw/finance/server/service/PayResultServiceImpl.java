package com.rw.finance.server.service;

import java.util.Date;

import com.rw.finance.common.constants.*;
import com.rw.finance.server.runner.MemberInfoRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.alibaba.dubbo.config.annotation.Service;
import com.google.gson.Gson;
import com.rw.finance.common.entity.AgentAccount;
import com.rw.finance.common.entity.AgentInfo;
import com.rw.finance.common.entity.AgentProfit;
import com.rw.finance.common.entity.MemberAccount;
import com.rw.finance.common.entity.MemberCard;
import com.rw.finance.common.entity.MemberInfo;
import com.rw.finance.common.entity.MemberProfit;
import com.rw.finance.common.entity.OrderCount;
import com.rw.finance.common.entity.OrderInfo;
import com.rw.finance.common.entity.RepayTask;
import com.rw.finance.common.entity.order.MemberActiveOrder;
import com.rw.finance.common.entity.order.MemberBorrowOrder;
import com.rw.finance.common.entity.order.MemberCardOrder;
import com.rw.finance.common.entity.order.MemberCashOrder;
import com.rw.finance.common.entity.order.RepayTaskOrder;
import com.rw.finance.common.pass.chuangxin.ChuangXinPay;
import com.rw.finance.common.pay.JdsoftPayer;
import com.rw.finance.common.pay.PayResult;
import com.rw.finance.common.pay.PayerBo;
import com.rw.finance.common.pay.PayerFactory;
import com.rw.finance.common.pay.YeeBao2Payer;
import com.rw.finance.common.pay.YeeBaoPayer;
import com.rw.finance.common.service.AgentProfitService;
import com.rw.finance.common.service.MemberProfitService;
import com.rw.finance.common.service.PayResultService;
import com.rw.finance.common.utils.DateUtils;
import com.rw.finance.common.utils.MathUtils;
import com.rw.finance.common.utils.SmsUtils;
import com.rw.finance.common.utils.UuidUtil;
import com.rw.finance.server.config.SystemSetting;
import com.rw.finance.server.dao.AgentAccountDao;
import com.rw.finance.server.dao.AgentInfoDao;
import com.rw.finance.server.dao.MemberAccountDao;
import com.rw.finance.server.dao.MemberCardDao;
import com.rw.finance.server.dao.MemberInfoDao;
import com.rw.finance.server.dao.MemberLevelDao;
import com.rw.finance.server.dao.OrderCountDao;
import com.rw.finance.server.dao.OrderInfoDao;
import com.rw.finance.server.dao.PayChannelDao;
import com.rw.finance.server.dao.RepayPlanDao;
import com.rw.finance.server.dao.RepayTaskDao;
/**
 * 
 * @file PayResultServiceImpl.java	
 * @author huanghongfei
 * @date 2018年1月12日 上午11:53:13
 * @declaration
 */
@Component
@Service(interfaceClass=PayResultService.class)
public class PayResultServiceImpl implements PayResultService{

	private static final Logger log=LoggerFactory.getLogger(PayResultServiceImpl.class);
	
	@Autowired
	private OrderInfoDao orderInfoDao;
	@Autowired
	private MemberInfoDao memberInfoDao;
	@Autowired
	private AgentInfoDao agentInfoDao;
	@Autowired
	private RepayPlanDao repayPlanDao;
	@Autowired
	private RepayTaskDao repayTaskDao;
	@Autowired
	private MemberLevelDao memberLevelDao;
	@Autowired
	private SystemSetting systemSetting;
	@Autowired
	private MemberProfitService memberProfitService;
	@Autowired
	private AgentProfitService agentProfitService;
	@Autowired
	private MemberAccountDao memberAccountDao;
	@Autowired
	private AgentAccountDao agentAccountDao;
	@Autowired
	private MemberCardDao memberCardDao;
	@Autowired
	private PayChannelDao payChannelDao;
	@Autowired
	private OrderCountDao orderCountDao;
	
	
	@Override
	@Transactional(rollbackFor=Exception.class)
	public void chuangXinPayBack(String tradeNo) {
		ChuangXinPay.PayOrder payOrder=new Gson().fromJson(new PayerFactory().ChuangXinPayer().queryOrder(new com.rw.finance.common.pay.PayerBo().new OrderInfo(tradeNo, "", 0 , "", "")).getResult(), ChuangXinPay.PayOrder.class);
		if(payOrder.getDealMsg().equals("交易成功")){
			payOrder.setOrderStatus(1);
		}
		com.rw.finance.common.entity.OrderInfo orderInfo=orderInfoDao.findByTradeno(tradeNo);
		if(orderInfo.getStatus().intValue()!=OrderInfoConstants.Status.STATUS0.getStatus()){
			log.info("order info excuted,tradeNo:{}",tradeNo);
			return ;
		}
		orderInfo.setRemark(payOrder.getDealMsg());
		int status=payOrder.getOrderStatus()==1?OrderInfoConstants.Status.STATUS1.getStatus():OrderInfoConstants.Status.STATUS2.getStatus();
		orderInfo.setStatus(status);
		orderInfo.setOuttradeno(StringUtils.isEmpty(payOrder.getCxOrderNo())?orderInfo.getOuttradeno():payOrder.getCxOrderNo());
		orderInfo.setUpdatetime(DateUtils.getTimeStr(new Date()));
		orderInfoDao.saveAndFlush(orderInfo);
		this.success(orderInfo);
	}
	
	@Override
	public void yeeBaoPayBack(String tradeNo) {
		PayResult payResult=new PayerFactory().YeeBaoPayPayer().queryOrder(new PayerBo().new OrderInfo(tradeNo, "", 0D, new YeeBaoPayer().getBackUrl(),""));
		com.rw.finance.common.entity.OrderInfo orderInfo=orderInfoDao.findByTradeno(tradeNo);
		if(orderInfo.getStatus().intValue()!=OrderInfoConstants.Status.STATUS0.getStatus()){
			log.info("order info excuted,tradeNo:{}",tradeNo);
			return ;
		}
		orderInfo.setStatus(payResult.getSuccess()?OrderInfoConstants.Status.STATUS1.getStatus():OrderInfoConstants.Status.STATUS2.getStatus());
		orderInfo.setOuttradeno(StringUtils.isEmpty(payResult.getPayTradeNo())?orderInfo.getOuttradeno():payResult.getPayTradeNo());
		orderInfo.setUpdatetime(DateUtils.getTimeStr(new Date()));
		orderInfoDao.saveAndFlush(orderInfo);
		this.success(orderInfo);
	}
	
	@Override
	public void yeeBao2PayBack(String tradeNo) {
		com.rw.finance.common.entity.OrderInfo orderInfo=orderInfoDao.findByTradeno(tradeNo);
		if(orderInfo.getStatus().intValue()!=OrderInfoConstants.Status.STATUS0.getStatus()){
			log.info("order info excuted,tradeNo:{}",tradeNo);
			return ;
		}
		PayResult payResult=new YeeBao2Payer().queryOrder(new PayerBo().new OrderInfo(tradeNo,orderInfo.getOuttradeno(), 0, new YeeBao2Payer().getBackUrl(),""));
		orderInfo.setStatus(payResult.getSuccess()?OrderInfoConstants.Status.STATUS1.getStatus():OrderInfoConstants.Status.STATUS2.getStatus());
		orderInfo.setOuttradeno(StringUtils.isEmpty(payResult.getPayTradeNo())?orderInfo.getOuttradeno():payResult.getPayTradeNo());
		orderInfo.setUpdatetime(DateUtils.getTimeStr(new Date()));
		orderInfoDao.saveAndFlush(orderInfo);
		this.success(orderInfo);
	}

	@Override
	public void yeeBao2AgentPayBack(String tradeNo) {
		com.rw.finance.common.entity.OrderInfo orderInfo=orderInfoDao.findByTradeno(tradeNo);
		if(orderInfo.getStatus().intValue()!=OrderInfoConstants.Status.STATUS0.getStatus()){
			log.info("order info excuted,tradeNo:{}",tradeNo);
			return ;
		}
		PayResult payResult=new YeeBao2Payer().queryAgentOrder(new PayerBo().new OrderInfo(tradeNo,orderInfo.getOuttradeno(), 0, new YeeBao2Payer().getBackUrl(),""));
		orderInfo.setStatus(payResult.getSuccess()?OrderInfoConstants.Status.STATUS1.getStatus():OrderInfoConstants.Status.STATUS2.getStatus());
		orderInfo.setOuttradeno(StringUtils.isEmpty(payResult.getPayTradeNo())?orderInfo.getOuttradeno():payResult.getPayTradeNo());
		orderInfo.setUpdatetime(DateUtils.getTimeStr(new Date()));
		orderInfoDao.saveAndFlush(orderInfo);
		this.success(orderInfo);
	}
	
	@Override
	public void jdsoftPayBack(String tradeNo) {
		com.rw.finance.common.entity.OrderInfo orderInfo=orderInfoDao.findByTradeno(tradeNo);
		if(orderInfo.getStatus().intValue()!=OrderInfoConstants.Status.STATUS0.getStatus()){
			log.info("order info excuted,tradeNo:{}",tradeNo);
			return ;
		}
		PayResult payResult=new JdsoftPayer().queryOrder(new PayerBo().new OrderInfo(tradeNo,orderInfo.getOuttradeno(),orderInfo.getOrderamount(), new JdsoftPayer().getBackUrl(),""));
		orderInfo.setStatus(payResult.getSuccess()?OrderInfoConstants.Status.STATUS1.getStatus():OrderInfoConstants.Status.STATUS2.getStatus());
		orderInfo.setOuttradeno(StringUtils.isEmpty(payResult.getPayTradeNo())?orderInfo.getOuttradeno():payResult.getPayTradeNo());
		orderInfo.setUpdatetime(DateUtils.getTimeStr(new Date()));
		orderInfoDao.saveAndFlush(orderInfo);
		//套现订单
		if(orderInfo.getType().intValue()==OrderInfoConstants.Type.MemberBorrowOrder.getType()){
			MemberBorrowOrder memberBorrowOrder=new Gson().fromJson(orderInfo.getDetails(), MemberBorrowOrder.class);
			//绝顶支付是一步操作，直接扣款收款同时成功
			if(orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){
				MemberBorrowOrder copyMemberBorrowOrder=new MemberBorrowOrder(memberBorrowOrder.getFromCardId(), memberBorrowOrder.getToCardId(), memberBorrowOrder.getPoundage());
				copyMemberBorrowOrder.setFromSucc(0);
				copyMemberBorrowOrder.setToSucc(1);
				orderInfo.setDetails(new Gson().toJson(copyMemberBorrowOrder));
				orderInfoDao.saveAndFlush(orderInfo);
				//收款分润
				MemberInfo memberInfo=memberInfoDao.findOne(orderInfo.getUserid());
				double memberProfitTotal=0;
				if(payResult.getSuccess()&&!StringUtils.isEmpty(memberInfo.getParentid())){
					memberProfitTotal=this.borrowMemberProfit(memberInfo.getParentid(), memberInfo, orderInfo, 1, 3,memberProfitTotal);
				}
				double agentProfitTotal=0;
				if(payResult.getSuccess()&&!StringUtils.isEmpty(memberInfo.getAgentid())){//代理分润
					agentProfitTotal=this.borrowAgentProfit(memberInfo.getAgentid(), memberInfo, orderInfo, memberProfitTotal,agentProfitTotal,0);
				}
				if(orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){
					double companyProfitTotal=MathUtils.subtract(new Gson().fromJson(orderInfo.getDetails(),MemberBorrowOrder.class).getPoundage(),memberProfitTotal);
					companyProfitTotal=MathUtils.subtract(companyProfitTotal, agentProfitTotal);
					orderCountDao.save(new OrderCount(orderInfo.getTradeno(),OrderCountConstants.TradeType.MemberBorrow.getTradeType(), orderInfo.getRealamount(), memberProfitTotal,agentProfitTotal,companyProfitTotal));
				}
			}
		}
	}

	/**
	 * 递归计算代理收益
	 * @param agentid 父代理编号
	 * @param memberInfo 会员信息
	 * @param repayTask 还款任务
	 * @param orderInfo 订单信息
	 * @param memberProfitTotal 计算代理收益时需减去会员总收益
	 * @return 代理总收益
	 */
	private double repayTaskAgentProfit(long agentid,MemberInfo memberInfo,OrderInfo orderInfo,double memberProfitTotal,double agentProfitTotal,double lastAgentProfitRate){
		AgentInfo parent=agentInfoDao.findOne(agentid);
		if(StringUtils.isEmpty(parent)){
			return agentProfitTotal;
		}
		//代理分润=总费率减去支付平台费率乘以代理等级结算费率
		AgentAccount agentAccount=agentAccountDao.findByAgentid(parent.getAgentid());
		double repayShareRate=agentAccount.getRepaysharerate();
		if(!StringUtils.isEmpty(parent.getParentid())&&memberInfo.getAgentid().longValue()!=agentid){//非直属代理
			repayShareRate=agentAccount.getRepaysharerate().doubleValue()-lastAgentProfitRate;
			memberProfitTotal=0;//非直属代理不承担会员分润
		}
		//会员还款代理分润比例乘以总金额，再乘以代理分润比例，最后减去会员总分润
		double agentProfit=MathUtils.multiply(MathUtils.multiply(orderInfo.getOrderamount(),systemSetting.MEMBER_REPAY_AGENT_PROFIT_TOTAL_RATE()),repayShareRate);
		agentProfit=MathUtils.subtract(agentProfit,memberProfitTotal)<0?0:MathUtils.subtract(agentProfit,memberProfitTotal);//直属代理承担会员分润，如果不是直属代理，会员分润为0
		agentProfitTotal=MathUtils.add(agentProfitTotal,agentProfit);
		agentProfitService.add(new AgentProfit(parent.getAgentid(),memberInfo.getMemberid(),orderInfo.getOrderid(),orderInfo.getTradeno(),agentAccount.getRepaysharerate() ,orderInfo.getOrderamount(), agentProfit,AgentProfitConstants.Type.RepayTaskProfit.getType()));
		//逻辑结束，递归调用
		if(StringUtils.isEmpty(parent.getParentid())){
			return agentProfitTotal;
		}
		lastAgentProfitRate=agentAccount.getRepaysharerate();
		this.repayTaskAgentProfit(parent.getParentid(),memberInfo,orderInfo,memberProfitTotal,agentProfitTotal,lastAgentProfitRate);
		return agentProfitTotal;
	}
	
	/**
	 * 递归调用，计算会员收益
	 * @param memberid 父会员编号
	 * @param memberInfo 会员信息
	 * @param repayTask 还款任务信息
	 * @param payResult 支付结果信息
	 * @param tradeNo 订单流水号
	 * @param INITIAL_LEVEL 初始化向上等级高度
	 * @param HEIGHT_LEVEL 最高向上等级高度
	 */
	private double repayTaskMemberProfit(long memberid,MemberInfo memberInfo,OrderInfo orderInfo,int INITIAL_LEVEL,int HEIGHT_LEVEL,double memberProfitTotal){
		MemberInfo parent=memberInfoDao.findOne(memberid);
		if(StringUtils.isEmpty(parent)){
			return memberProfitTotal;
		}
		double memerProfit=MathUtils.multiply(orderInfo.getOrderamount(),systemSetting.MEMBER_PROFIT_REPAY_LEVEL_RATE(INITIAL_LEVEL));//用户等级级分润
		memberProfitTotal=MathUtils.add(memberProfitTotal, memerProfit);//叠加会员总收益
		memberProfitService.add(new MemberProfit(
				parent.getMemberid(),
				memberInfo.getMemberid(),//产生这比利润的会员编号
				memberInfo.getTelephone(),//贡献账户
				MemberProfitConstants.BizType.RepayTaskProfit.getBizType(), 
				orderInfo.getOrderamount(),
				memerProfit, //收益
				orderInfo.getTradeno(),
				INITIAL_LEVEL++));
		log.info("repayTask created memberProfit:{} by level:{} for memberid:{} in repayTask.taskamount:{}",memerProfit,MemberProfitConstants.Level.LEVEL1.getLevel(),memberInfo.getMemberid(),orderInfo.getOrderamount());
		if(INITIAL_LEVEL>HEIGHT_LEVEL||StringUtils.isEmpty(parent.getParentid())){
			return memberProfitTotal;//超过最高树的高度，递归结束
		}
		memberProfitTotal=this.repayTaskMemberProfit(parent.getParentid(), memberInfo,orderInfo,INITIAL_LEVEL,HEIGHT_LEVEL,memberProfitTotal);
		return memberProfitTotal;
	}
	/**
	 * 递归向上计算激活的代理收益,只有在线支付激活才存在分润
	 * @param memberInfo
	 * @param memberProfitTotal 会员总分润，计算代理收益时需减去会员总分润
	 * @param lastAgentProfitRate 分润是从低级代理到高级代理的顺序，该参数代表最后一个分润的比例
	 */
	private double activeAgentProfit(long agentid,MemberInfo memberInfo,OrderInfo orderInfo,double memberProfitTotal,double agentProfitTotal,double lastAgentProfitRate){
		AgentInfo parent=agentInfoDao.findOne(agentid);
		if(StringUtils.isEmpty(parent)){
			return agentProfitTotal;
		}
		AgentAccount agentAccount=agentAccountDao.findByAgentid(parent.getAgentid());//当前代理的账户
		double activeShareRate=agentAccount.getActivatesharerate();
		if(!StringUtils.isEmpty(parent.getParentid())&&memberInfo.getAgentid().longValue()!=agentid){//非直属代理
			activeShareRate=agentAccount.getActivatesharerate().doubleValue()-lastAgentProfitRate;
			memberProfitTotal=0;//非直属代理不承担会员分润
		}
		double agentProfit=MathUtils.multiply(orderInfo.getOrderamount(),activeShareRate);//我们承担支付平台费用
		agentProfit=MathUtils.subtract(agentProfit,memberProfitTotal)<0?0:MathUtils.subtract(agentProfit,memberProfitTotal);//直属代理承担会员分润，如果不是直属代理，会员分润为0
		agentProfitTotal=MathUtils.add(agentProfitTotal, agentProfit);
		agentProfitService.add(new AgentProfit(parent.getAgentid(), memberInfo.getMemberid(),orderInfo.getOrderid(),orderInfo.getTradeno(), agentAccount.getActivatesharerate(), orderInfo.getOrderamount(), agentProfit, AgentProfitConstants.Type.MemberActiveProfit.getType()));
		if(StringUtils.isEmpty(parent.getParentid())){
			return agentProfitTotal;
		}
		lastAgentProfitRate=agentAccount.getActivatesharerate();//当前代理分润比例
		agentProfitTotal=this.activeAgentProfit(parent.getParentid(), memberInfo,orderInfo,memberProfitTotal,agentProfitTotal,lastAgentProfitRate);
		return agentProfitTotal;
	}
	/**
	 * 递归向上计算激活的邀请会员收益，只有在线激活才会存在，向上查找3层
	 * @param memberId
	 * @param memberInfo
	 * @param tradeAmount
	 * @param memberProfitTotal 统计会员总分润
	 */
	private double activeMemberProfit(long memberId,MemberInfo memberInfo,OrderInfo orderInfo,int INITIAL_LEVEL,int HEIGHT_LEVEL,double memberProfitTotal){
		MemberInfo parent=memberInfoDao.findOne(memberId);
		if(StringUtils.isEmpty(parent)){
			return memberProfitTotal;
		}
		double memerProfit=MathUtils.multiply(orderInfo.getOrderamount(),systemSetting.MEMBER_PROFIT_ACTIVE_LEVEL_RATE(INITIAL_LEVEL));//用户等级级分润
		memberProfitTotal=MathUtils.add(memberProfitTotal, memerProfit);
		memberProfitService.add(new MemberProfit(
				parent.getMemberid(),
				memberInfo.getMemberid(),//产生这比利润的会员编号
				memberInfo.getTelephone(),//贡献账户
				MemberProfitConstants.BizType.MemberActiveProfit.getBizType(), 
				orderInfo.getOrderamount(),
				memerProfit, //收益
				orderInfo.getTradeno(),
				INITIAL_LEVEL++));
		if(INITIAL_LEVEL>HEIGHT_LEVEL||StringUtils.isEmpty(parent.getParentid())){
			return memberProfitTotal;//超过最高树的高度，递归结束
		}
		memberProfitTotal=this.activeMemberProfit(parent.getParentid(), memberInfo, orderInfo,INITIAL_LEVEL,HEIGHT_LEVEL,memberProfitTotal);
		return memberProfitTotal;
	}
	/**
	 * 递归计算代理收益
	 * @param agentid 父代理编号
	 * @param memberInfo 会员信息
	 * @param orderInfo 订单信息
	 * @param memberProfitTotal 计算代理收益时需减去会员总收益
	 */
	private double borrowAgentProfit(long agentid,MemberInfo memberInfo,OrderInfo orderInfo,double memberProfitTotal,double agentProfitTotal,double lastAgentProfitRate){
		AgentInfo parent=agentInfoDao.findOne(agentid);
		if(StringUtils.isEmpty(parent)){
			return agentProfitTotal;
		}
		//代理分润=总费率减去支付平台费率乘以代理等级结算费率
		AgentAccount agentAccount=agentAccountDao.findByAgentid(parent.getAgentid());
		double borrowShareRate=agentAccount.getBorrowsharerate();
		if(!StringUtils.isEmpty(parent.getParentid())&&memberInfo.getAgentid().longValue()!=agentid){//非直属代理
			borrowShareRate=agentAccount.getBorrowsharerate().doubleValue()-lastAgentProfitRate;
			memberProfitTotal=0;//非直属代理不承担会员分润
		}
		//总利润减去支付平台利润，减去会员分享总分润，最后乘代理分享分润比例
		double agentProfit=MathUtils.multiply(orderInfo.getOrderamount(),borrowShareRate);
		agentProfit=MathUtils.subtract(agentProfit,memberProfitTotal)<0?0:MathUtils.subtract(agentProfit,memberProfitTotal);//直属代理承担会员分润，如果不是直属代理，会员分润为0
		agentProfitTotal=MathUtils.add(agentProfitTotal,agentProfit);
		agentProfitService.add(new AgentProfit(parent.getAgentid(),memberInfo.getMemberid(),orderInfo.getOrderid(),orderInfo.getTradeno(),agentAccount.getBorrowsharerate() ,orderInfo.getOrderamount(), agentProfit,AgentProfitConstants.Type.MemberBorrowProfit.getType()));
		//逻辑结束，递归调用
		if(StringUtils.isEmpty(parent.getParentid())){
			return agentProfitTotal;
		}
		lastAgentProfitRate=agentAccount.getBorrowsharerate();
		agentProfitTotal=this.borrowAgentProfit(parent.getParentid(),memberInfo,orderInfo,memberProfitTotal,agentProfitTotal,lastAgentProfitRate);
		return agentProfitTotal;
	}
	/**
	 * 会员套现邀请者分润
	 * @param memberid
	 * @param memberInfo
	 * @param bizAmount
	 * @param tradeNo
	 * @param INITIAL_LEVEL
	 * @param HEIGHT_LEVEL
	 */
	private double borrowMemberProfit(long memberid,MemberInfo memberInfo,OrderInfo orderInfo,int INITIAL_LEVEL,int HEIGHT_LEVEL,double memberProfitTotal){
		MemberInfo parent=memberInfoDao.findOne(memberid);
		if(StringUtils.isEmpty(parent)){
			return memberProfitTotal;
		}
		//邀请用户分润=总费率减去支付平台费率然后乘以1级代理用户分润利率
		double memerProfit=MathUtils.multiply(orderInfo.getOrderamount(),systemSetting.MEMBER_PROFIT_BORROW_LEVEL_RATE(INITIAL_LEVEL));//用户等级级分润
		memberProfitTotal=MathUtils.add(memberProfitTotal, memerProfit);
		memberProfitService.add(new MemberProfit(
				parent.getMemberid(),
				memberInfo.getMemberid(),//产生这比利润的会员编号
				memberInfo.getTelephone(),//贡献账户
				MemberProfitConstants.BizType.MemberBorrowProfit.getBizType(), 
				orderInfo.getOrderamount(),
				memerProfit, //收益
				orderInfo.getTradeno(),
				INITIAL_LEVEL++));
		if(INITIAL_LEVEL>HEIGHT_LEVEL||StringUtils.isEmpty(parent.getParentid())){
			return memberProfitTotal;//超过最高树的高度，递归结束
		}
		memberProfitTotal=this.borrowMemberProfit(parent.getParentid(), memberInfo,orderInfo,INITIAL_LEVEL,HEIGHT_LEVEL,memberProfitTotal);
		return memberProfitTotal;
	}
	/**
	 * 会员提现邀请会员分润
	 * @param memberid
	 * @param memberInfo
	 * @param bizAmount
	 * @param tradeNo
	 * @param INITIAL_LEVEL
	 * @param HEIGHT_LEVEL
	 */
	public double cashMemberProfit(long memberid,MemberInfo memberInfo,OrderInfo orderInfo,int INITIAL_LEVEL,int HEIGHT_LEVEL,double memberProfitTotal){
		MemberInfo parent=memberInfoDao.findOne(memberid);
		if(StringUtils.isEmpty(parent)){
			return memberProfitTotal;
		}
		//邀请用户分润=总费率减去支付平台费率然后乘以1级代理用户分润利率
		double memerProfit=MathUtils.multiply(orderInfo.getOrderamount(),systemSetting.MEMBER_PROFIT_CASH_LEVEL_RATE(INITIAL_LEVEL));//用户等级级分润
		memberProfitTotal=MathUtils.add(memberProfitTotal,memerProfit);
		memberProfitService.add(new MemberProfit(
				parent.getMemberid(),
				memberInfo.getMemberid(),//产生这比利润的会员编号
				memberInfo.getTelephone(),//贡献账户
				MemberProfitConstants.BizType.MemberCashProfit.getBizType(), 
				orderInfo.getOrderamount(),
				memerProfit, //收益
				orderInfo.getTradeno(),
				INITIAL_LEVEL++));
		if(INITIAL_LEVEL>HEIGHT_LEVEL||StringUtils.isEmpty(parent.getParentid())){
			return memberProfitTotal;//超过最高树的高度，递归结束
		}
		memberProfitTotal=this.cashMemberProfit(parent.getParentid(), memberInfo,orderInfo,INITIAL_LEVEL,HEIGHT_LEVEL,memberProfitTotal);
		return memberProfitTotal;
	}
	
	/**
	 * 处理支付成功逻辑
	 * @return
	 */
	private void success(OrderInfo orderInfo){
		//还款任务订单
		if(orderInfo.getType().intValue()==OrderInfoConstants.Type.RepayTaskOrder.getType()){
			RepayTask repayTask=repayTaskDao.findByTaskid(new Gson().fromJson(orderInfo.getDetails(),RepayTaskOrder.class).getTaskId());
			int repayTaskStatus=orderInfo.getStatus().intValue()==RepayTaskConstants.Status.STATUS1.getStatus()?RepayTaskConstants.Status.STATUS1.getStatus():RepayTaskConstants.Status.STATUS2.getStatus();
			repayTask.setStatus(repayTaskStatus);
			repayTaskDao.saveAndFlush(repayTask);
			MemberInfo memberInfo=memberInfoDao.findOne(orderInfo.getUserid());
			//任务失败发送消息给会员
			if(repayTaskStatus!=RepayTaskConstants.Status.STATUS1.getStatus()){
				SmsUtils.sendError(memberInfo.getTelephone(),"还款任务");
			}
			//开始计算会员收益,向上查找
			double memberProfitTotal=0;//会员总分润，代理分润时需要在总利润里面扣除会员分享总分润
			if(!StringUtils.isEmpty(memberInfo.getParentid())&&orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()&&repayTask.getTasktype().intValue()==RepayTaskConstants.Type.TYPE0.getType()){
				memberProfitTotal=this.repayTaskMemberProfit(memberInfo.getParentid(), memberInfo,orderInfo,1,3,memberProfitTotal);
			}
			//开始计算代理收益
			double agentProfitTotal=0;//代理总收益
			if(!StringUtils.isEmpty(memberInfo.getAgentid())&&orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()&&repayTask.getTasktype().intValue()==RepayTaskConstants.Type.TYPE0.getType()){
				agentProfitTotal=this.repayTaskAgentProfit(memberInfo.getAgentid(),memberInfo,orderInfo,memberProfitTotal,agentProfitTotal,0);
			}
			//统计
			if(orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){
				double companyProfitTotal=MathUtils.subtract(repayTask.getPoundage(),MathUtils.add(memberProfitTotal, agentProfitTotal));
				orderCountDao.save(new OrderCount(orderInfo.getTradeno(),OrderCountConstants.TradeType.RepayTask.getTradeType(), orderInfo.getRealamount(), memberProfitTotal, agentProfitTotal,companyProfitTotal));
			}
		}
		//会员提现订单
		if(orderInfo.getType().intValue()==OrderInfoConstants.Type.MemberCashOrder.getType()){
			MemberAccount memberAccount=memberAccountDao.findByMemberid(orderInfo.getUserid());
			if(orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){//成功，减去锁定余额
				memberAccount.setLockbalance(MathUtils.subtract(memberAccount.getLockbalance(),orderInfo.getRealamount()));
			}else{//失败，将锁定余额返还到可用余额
				memberAccount.setLockbalance(MathUtils.subtract(memberAccount.getLockbalance(),orderInfo.getRealamount()));
				memberAccount.setUsablebalance(MathUtils.add(memberAccount.getUsablebalance(), orderInfo.getRealamount()));
			}
			memberAccountDao.saveAndFlush(memberAccount);
			//会员提现分润
			MemberInfo memberInfo=memberInfoDao.findOne(orderInfo.getUserid());
			double memberProfitTotal=0;
			if(orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()&&!StringUtils.isEmpty(memberInfo.getParentid())){
				memberProfitTotal=this.cashMemberProfit(memberInfo.getParentid(), memberInfo, orderInfo, 1, 3,memberProfitTotal);
			}
			if(orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){
				double companyProfitTotal=MathUtils.subtract(new Gson().fromJson(orderInfo.getDetails(),MemberCashOrder.class).getPoundage(),memberProfitTotal);
				orderCountDao.save(new OrderCount(orderInfo.getTradeno(),OrderCountConstants.TradeType.MemberCash.getTradeType(), orderInfo.getRealamount(), memberProfitTotal, 0,companyProfitTotal));
			}
			
		}
		//收款订单(套现)
		if(orderInfo.getType().intValue()==OrderInfoConstants.Type.MemberBorrowOrder.getType()){
			MemberBorrowOrder memberBorrowOrder=new Gson().fromJson(orderInfo.getDetails(), MemberBorrowOrder.class);
			//扣款未成功，并且这次回调为订单交易成功，说明扣款成功
			if(memberBorrowOrder.getFromSucc()==0&&orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){
				MemberBorrowOrder copyMemberBorrowOrder=new MemberBorrowOrder(memberBorrowOrder.getFromCardId(), memberBorrowOrder.getToCardId(), memberBorrowOrder.getPoundage());
				copyMemberBorrowOrder.setFromSucc(1);
				orderInfo.setDetails(new Gson().toJson(copyMemberBorrowOrder));
				orderInfoDao.saveAndFlush(orderInfo);
				//扣款成功，向会员储蓄卡账户打钱
				MemberCard memberCard=memberCardDao.findOne(memberBorrowOrder.getToCardId());
				String tradeNo=UuidUtil.tradeNoBuilder(OrderInfoConstants.Prefix.MemberBorrowOrder.getPrefix());
				PayerBo.OrderInfo oi=new PayerBo().new OrderInfo(tradeNo,"",orderInfo.getRealamount(),new PayerFactory().DefaultPayer().getBackUrl(),"");
				oi.setRemark(orderInfo.getTradeno());
				PayResult payResult=new PayerFactory().DefaultPayer().repay(new PayerBo().new UserInfo(memberCard.getIdnumber(),memberCard.getRealname()),
						new PayerBo().new CardInfo(memberCard.getBankname(),memberCard.getProvince(),memberCard.getCity(),memberCard.getAbbreviation(),memberCard.getCardno(), memberCard.getMobile(), memberCard.getAuthcode(), memberCard.getExpirydate()),
						oi);
				MemberBorrowOrder repayMemberBorrowOrder=new MemberBorrowOrder(memberBorrowOrder.getFromCardId(), memberBorrowOrder.getToCardId(),0);
				MemberInfo memberInfo=memberInfoDao.findOne(orderInfo.getUserid());
				if(payResult.getSuccess()){
					repayMemberBorrowOrder.setToSucc(1);
				}else{
					SmsUtils.sendError(memberInfo.getTelephone(),"收款操作");
				}
				OrderInfo repayOrderInfo=new OrderInfo(orderInfo.getUserid(),memberInfo.getRealname(), tradeNo, orderInfo.getRealamount(), //这次订单为付款给会员，付款的实际金额为套现的订单金额
						orderInfo.getOrderamount(),payChannelDao.findByIsdef(1).getChannelid(), "", OrderInfoConstants.Type.MemberBorrowOrder.getType(),"", 
						new Gson().toJson(repayMemberBorrowOrder));
				//创新支付会马上拿到同名代付结果
				repayOrderInfo.setStatus(payResult.getSuccess()?OrderInfoConstants.Status.STATUS1.getStatus():OrderInfoConstants.Status.STATUS2.getStatus());
				repayOrderInfo.setRemark(payResult.getDetails());
				orderInfoDao.save(repayOrderInfo);
				double memberProfitTotal=0;
				if(payResult.getSuccess()&&!StringUtils.isEmpty(memberInfo.getParentid())){
					memberProfitTotal=this.borrowMemberProfit(memberInfo.getParentid(), memberInfo, orderInfo, 1, 3,memberProfitTotal);
				}
				double agentProfitTotal=0;
				if(payResult.getSuccess()&&!StringUtils.isEmpty(memberInfo.getAgentid())){//代理分润
					agentProfitTotal=this.borrowAgentProfit(memberInfo.getAgentid(), memberInfo, repayOrderInfo, memberProfitTotal,agentProfitTotal,0);
				}
				if(repayOrderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){
					double companyProfitTotal=MathUtils.subtract(new Gson().fromJson(orderInfo.getDetails(),MemberBorrowOrder.class).getPoundage(),memberProfitTotal);
					companyProfitTotal=MathUtils.subtract(companyProfitTotal, agentProfitTotal);
					orderCountDao.save(new OrderCount(orderInfo.getTradeno(),OrderCountConstants.TradeType.MemberBorrow.getTradeType(), orderInfo.getRealamount(), memberProfitTotal,agentProfitTotal,companyProfitTotal));
				}
			}
		}
		//会员在线支付激活订单
		if(orderInfo.getType().intValue()==OrderInfoConstants.Type.MemberActiveOrder.getType()&&orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){
			MemberInfo memberInfo=memberInfoDao.findOne(orderInfo.getUserid());
			memberInfo.setLevel(new Gson().fromJson(orderInfo.getDetails(),MemberActiveOrder.class).getActiveLevel());
			memberInfo.setLeveltime(MemberInfoConstants.LEVEL_TIME_DEFAULT);
			memberInfo.setActivetime(DateUtils.getTimeStr(new Date()));
			memberInfoDao.saveAndFlush(memberInfo);
			double memberProfitTotal=0;
			if(!StringUtils.isEmpty(memberInfo.getParentid())){//分享会员分润
				memberProfitTotal=this.activeMemberProfit(memberInfo.getParentid(), memberInfo,orderInfo, 1, 3,memberProfitTotal);
			}
			double agentProfitTotal=0;
			if(!StringUtils.isEmpty(memberInfo.getAgentid())){//代理分润
				agentProfitTotal=this.activeAgentProfit(memberInfo.getAgentid(), memberInfo,orderInfo,memberProfitTotal,agentProfitTotal,0);
			}
			if(orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){
				double companyProfitTotal=MathUtils.subtract(orderInfo.getRealamount(),memberProfitTotal);
				companyProfitTotal=MathUtils.subtract(companyProfitTotal, agentProfitTotal);
				orderCountDao.save(new OrderCount(orderInfo.getTradeno(),OrderCountConstants.TradeType.MemberActive.getTradeType(), orderInfo.getRealamount(), memberProfitTotal,agentProfitTotal,companyProfitTotal));
			}
		}
		//代理提现订单
		if(orderInfo.getType().intValue()==OrderInfoConstants.Type.AgentCashOrder.getType()){
			AgentAccount agentAccount=agentAccountDao.findByAgentid(orderInfo.getUserid());
			if(orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){//订单成功，扣除锁定余额
				agentAccount.setLockbalance(MathUtils.subtract(agentAccount.getLockbalance(),orderInfo.getRealamount()));
			}else{//订单失败，将锁定余额返还到可用余额
				agentAccount.setLockbalance(MathUtils.subtract(agentAccount.getLockbalance(),orderInfo.getRealamount()));
				agentAccount.setUsablebalance(MathUtils.add(agentAccount.getUsablebalance(),orderInfo.getRealamount()));
			}
			agentAccountDao.saveAndFlush(agentAccount);
		}
		//卡片鉴权订单
		if(orderInfo.getType().intValue()==OrderInfoConstants.Type.MemberCardOrder.getType()){
			MemberCard memberCard=memberCardDao.findOne(new Gson().fromJson(orderInfo.getDetails(),MemberCardOrder.class).getCardId());
			if(orderInfo.getStatus().intValue()==OrderInfoConstants.Status.STATUS1.getStatus()){
				memberCard.setStatus(MemberCardConstatns.Status.STATUS1.getStatus());
				memberCardDao.saveAndFlush(memberCard);
				orderCountDao.save(new OrderCount(orderInfo.getTradeno(),OrderCountConstants.TradeType.MemberCard.getTradeType(), orderInfo.getRealamount(), 0,0,orderInfo.getRealamount()));
			}else{//订单失败，为防止会员重新支付需删除卡片
				memberCardDao.delete(memberCard.getCardid());
			}
		}
		//其他订单
		if(orderInfo.getType().intValue()==OrderInfoConstants.Type.OtherOrder.getType()){
			//...
		}
	}


}
