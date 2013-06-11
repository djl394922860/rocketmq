/**
 * $Id: DefaultMQPushConsumerImpl.java 1831 2013-05-16 01:39:51Z shijia.wxr $
 */
package com.alibaba.rocketmq.client.impl.consumer;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.alibaba.rocketmq.client.QueryResult;
import com.alibaba.rocketmq.client.consumer.DefaultMQPushConsumer;
import com.alibaba.rocketmq.client.consumer.MQPushConsumer;
import com.alibaba.rocketmq.client.consumer.PullCallback;
import com.alibaba.rocketmq.client.consumer.PullResult;
import com.alibaba.rocketmq.client.consumer.listener.MessageListener;
import com.alibaba.rocketmq.client.consumer.store.LocalFileOffsetStore;
import com.alibaba.rocketmq.client.consumer.store.OffsetStore;
import com.alibaba.rocketmq.client.consumer.store.RemoteBrokerOffsetStore;
import com.alibaba.rocketmq.client.exception.MQBrokerException;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.client.impl.CommunicationMode;
import com.alibaba.rocketmq.client.impl.MQClientManager;
import com.alibaba.rocketmq.client.impl.factory.MQClientFactory;
import com.alibaba.rocketmq.client.log.ClientLogger;
import com.alibaba.rocketmq.common.ServiceState;
import com.alibaba.rocketmq.common.TopicFilterType;
import com.alibaba.rocketmq.common.help.FAQUrl;
import com.alibaba.rocketmq.common.message.MessageExt;
import com.alibaba.rocketmq.common.message.MessageQueue;
import com.alibaba.rocketmq.common.protocol.heartbeat.ConsumeType;
import com.alibaba.rocketmq.common.protocol.heartbeat.MessageModel;
import com.alibaba.rocketmq.common.protocol.heartbeat.SubscriptionData;
import com.alibaba.rocketmq.common.sysflag.PullSysFlag;
import com.alibaba.rocketmq.remoting.exception.RemotingException;


/**
 * @author shijia.wxr<vintage.wang@gmail.com>
 */
public class DefaultMQPushConsumerImpl implements MQPushConsumer, MQConsumerInner {
    private final Logger log = ClientLogger.getLog();
    private final DefaultMQPushConsumer defaultMQPushConsumer;
    private ServiceState serviceState = ServiceState.CREATE_JUST;
    private MQClientFactory mQClientFactory;
    private PullAPIWrapper pullAPIWrapper;

    // 消费进度存储
    private OffsetStore offsetStore;

    // 消息存储相关
    private ConcurrentHashMap<MessageQueue, ProcessQueue> processQueueTable =
            new ConcurrentHashMap<MessageQueue, ProcessQueue>(64);


    @Override
    public void start() throws MQClientException {
        switch (this.serviceState) {
        case CREATE_JUST:
            this.serviceState = ServiceState.RUNNING;

            this.mQClientFactory =
                    MQClientManager.getInstance().getAndCreateMQClientFactory(this.defaultMQPushConsumer);

            this.pullAPIWrapper = new PullAPIWrapper(//
                mQClientFactory,//
                this.defaultMQPushConsumer.getConsumerGroup(),//
                this.defaultMQPushConsumer.getConsumeFromWhichNode());

            // 广播消费/集群消费
            switch (this.defaultMQPushConsumer.getMessageModel()) {
            case BROADCASTING:
                this.offsetStore =
                        new LocalFileOffsetStore(this.mQClientFactory,
                            this.defaultMQPushConsumer.getConsumerGroup());
                break;
            case CLUSTERING:
                this.offsetStore =
                        new RemoteBrokerOffsetStore(this.mQClientFactory,
                            this.defaultMQPushConsumer.getConsumerGroup());
                break;
            case UNKNOWNS:
                break;
            default:
                break;
            }

            // 加载消费进度
            this.offsetStore.load();

            boolean registerOK =
                    mQClientFactory.registerConsumer(this.defaultMQPushConsumer.getConsumerGroup(), this);
            if (!registerOK) {
                this.serviceState = ServiceState.CREATE_JUST;
                throw new MQClientException("The consumer group[" + this.defaultMQPushConsumer.getConsumerGroup()
                        + "] has created already, specifed another name please."
                        + FAQUrl.suggestTodo(FAQUrl.GROUP_NAME_DUPLICATE_URL), null);
            }

            mQClientFactory.start();
            log.info("the consumer [{}] start OK", this.defaultMQPushConsumer.getConsumerGroup());
            break;
        case RUNNING:
            break;
        case SHUTDOWN_ALREADY:
            break;
        default:
            break;
        }
    }


    @Override
    public void shutdown() {
        switch (this.serviceState) {
        case CREATE_JUST:
            break;
        case RUNNING:
            this.serviceState = ServiceState.SHUTDOWN_ALREADY;
            // TODO 存储消费进度时，需要考虑集群模式覆盖掉其他消费者消费进度的问题
            this.persistConsumerOffset();
            this.mQClientFactory.unregisterConsumer(this.defaultMQPushConsumer.getConsumerGroup());
            this.mQClientFactory.shutdown();
            log.info("the consumer [{}] shutdown OK", this.defaultMQPushConsumer.getConsumerGroup());
            break;
        case SHUTDOWN_ALREADY:
            break;
        default:
            break;
        }
    }


    private void makeSureStateOK() throws MQClientException {
        if (this.serviceState != ServiceState.RUNNING) {
            throw new MQClientException("The consumer service state not OK, " + this.serviceState, null);
        }
    }


    public DefaultMQPushConsumerImpl(DefaultMQPushConsumer defaultMQPushConsumer) {
        this.defaultMQPushConsumer = defaultMQPushConsumer;
    }


    @Override
    public void sendMessageBack(MessageExt msg, MessageQueue mq, int delayLevel) {
        // TODO Auto-generated method stub
    }


    @Override
    public List<MessageQueue> fetchSubscribeMessageQueues(String topic) throws MQClientException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void createTopic(String key, String newTopic, int queueNum, TopicFilterType topicFilterType,
            boolean order) throws MQClientException {
        // TODO Auto-generated method stub

    }


    @Override
    public long searchOffset(MessageQueue mq, long timestamp) throws MQClientException {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public long getMaxOffset(MessageQueue mq) throws MQClientException {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public long getMinOffset(MessageQueue mq) throws MQClientException {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public long getEarliestMsgStoreTime(MessageQueue mq) throws MQClientException {
        // TODO Auto-generated method stub
        return 0;
    }


    @Override
    public MessageExt viewMessage(String msgId) throws RemotingException, MQBrokerException, InterruptedException,
            MQClientException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public QueryResult queryMessage(String topic, String key, int maxNum, long begin, long end)
            throws MQClientException, InterruptedException {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    public void registerMessageListener(MessageListener messageListener) {
        // TODO Auto-generated method stub

    }


    @Override
    public void subscribe(String topic, String subExpression) {
        // TODO Auto-generated method stub

    }


    @Override
    public void unsubscribe(String topic) {
        // TODO Auto-generated method stub

    }


    @Override
    public void suspend() {
        // TODO Auto-generated method stub

    }


    @Override
    public void resume() {
        // TODO Auto-generated method stub
    }


    @Override
    public String getGroupName() {
        return null;
    }


    @Override
    public MessageModel getMessageModel() {
        return null;
    }


    @Override
    public ConsumeType getConsumeType() {
        return ConsumeType.CONSUME_PASSIVELY;
    }


    @Override
    public Set<SubscriptionData> getMQSubscriptions() {
        return null;
    }


    /**
     * 单线程调用
     */
    private ProcessQueue getAndCreateProcessQueue(final MessageQueue mq) {
        ProcessQueue pq = this.processQueueTable.get(mq);
        if (null == pq) {
            pq = new ProcessQueue();
            this.processQueueTable.put(mq, pq);
        }

        return pq;
    }


    /**
     * 稍后再执行这个PullRequest
     */
    private void executePullRequestLater(final PullRequest pullRequest, final long timeDelay) {
        this.mQClientFactory.getPullMessageService().executePullRequestLater(pullRequest, timeDelay);
    }


    public void executePullRequestImmediately(final PullRequest pullRequest) {
        this.mQClientFactory.getPullMessageService().executePullRequestImmediately(pullRequest);
    }

    private final long PullTimeDelayMillsWhenException = 3000;
    private final long PullTimeDelayMillsWhenFlowControl = 1000;


    public void pullMessage(final PullRequest pullRequest) {
        final ProcessQueue processQueue = this.getAndCreateProcessQueue(pullRequest.getMessageQueue());

        // 流量控制
        long size = processQueue.getMsgCount().get();
        if (size > this.defaultMQPushConsumer.getConsumeConcurrentlyMaxSpan()) {
            this.executePullRequestLater(pullRequest, PullTimeDelayMillsWhenFlowControl);
            log.warn("the consumer message buffer is full, so do flow control, {} {}", size, pullRequest);
            return;
        }

        PullCallback pullCallback = new PullCallback() {
            @Override
            public void onSuccess(PullResult pullResult) {
                if (pullResult != null) {
                    pullResult =
                            DefaultMQPushConsumerImpl.this.pullAPIWrapper.processPullResult(
                                pullRequest.getMessageQueue(), pullResult);

                    switch (pullResult.getPullStatus()) {
                    case FOUND:
                        processQueue.putMessage(pullResult.getMsgFoundList());
                        break;
                    case NO_NEW_MSG:
                        DefaultMQPushConsumerImpl.this.executePullRequestImmediately(pullRequest);
                        break;
                    case NO_MATCHED_MSG:
                        pullRequest.setNextOffset(pullResult.getNextBeginOffset());
                        DefaultMQPushConsumerImpl.this.executePullRequestImmediately(pullRequest);
                        break;
                    case OFFSET_ILLEGAL:
                        log.warn("the pull request offset illegal, {} {}",//
                            pullRequest.toString(), pullResult.toString());
                        if (pullRequest.getNextOffset() < pullResult.getMinOffset()) {
                            pullRequest.setNextOffset(pullResult.getMinOffset());
                        }
                        else if (pullRequest.getNextOffset() > pullResult.getMaxOffset()) {
                            pullRequest.setNextOffset(pullResult.getMaxOffset());
                        }

                        log.warn("fix the pull request offset, {}", pullRequest);
                        DefaultMQPushConsumerImpl.this.executePullRequestImmediately(pullRequest);
                        break;
                    default:
                        break;
                    }
                }
            }


            @Override
            public void onException(Throwable e) {
                log.warn("execute the pull request exception", e);
                DefaultMQPushConsumerImpl.this.executePullRequestLater(pullRequest,
                    PullTimeDelayMillsWhenException);
            }
        };

        int sysFlag = PullSysFlag.buildSysFlag(//
            false, // commitOffset
            true, // suspend
            false// subscription
            );
        try {
            this.pullAPIWrapper.pullKernelImpl(//
                pullRequest.getMessageQueue(), // 1
                null, // 2
                pullRequest.getNextOffset(), // 3
                this.defaultMQPushConsumer.getPullBatchSize(), // 4
                sysFlag, // 5
                0,// 6
                1000 * 10, // 7
                1000 * 20, // 8
                CommunicationMode.ASYNC, // 9
                pullCallback// 10
                );
        }
        catch (Exception e) {
            log.error("pullKernelImpl exception", e);
            this.executePullRequestLater(pullRequest, PullTimeDelayMillsWhenException);
        }
    }


    @Override
    public void persistConsumerOffset() {
        try {
            this.makeSureStateOK();
            this.offsetStore.persistAll();
        }
        catch (Exception e) {
            log.error("group: " + this.defaultMQPushConsumer.getConsumerGroup()
                    + " persistConsumerOffset exception", e);
        }
    }
}
