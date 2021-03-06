/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */
package org.switchyard.component.bpm.exchange;

import static org.switchyard.component.common.knowledge.util.Mappings.getInputMap;
import static org.switchyard.component.common.knowledge.util.Mappings.getOutputMap;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.TransactionManager;
import javax.transaction.UserTransaction;

import org.drools.persistence.jta.JtaTransactionManager;
import org.jbpm.persistence.JpaProcessPersistenceContextManager;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.kie.runtime.EnvironmentName;
import org.kie.runtime.process.ProcessInstance;
import org.switchyard.Context;
import org.switchyard.Exchange;
import org.switchyard.ExchangePattern;
import org.switchyard.HandlerException;
import org.switchyard.Message;
import org.switchyard.Scope;
import org.switchyard.ServiceDomain;
import org.switchyard.component.bpm.BPMActionType;
import org.switchyard.component.bpm.BPMConstants;
import org.switchyard.component.bpm.config.model.BPMComponentImplementationModel;
import org.switchyard.component.bpm.transaction.AS7TransactionHelper;
import org.switchyard.component.bpm.work.WorkItemHandlers;
import org.switchyard.component.common.knowledge.KnowledgeConstants;
import org.switchyard.component.common.knowledge.exchange.KnowledgeAction;
import org.switchyard.component.common.knowledge.exchange.KnowledgeExchangeHandler;
import org.switchyard.component.common.knowledge.session.KnowledgeSession;
import org.switchyard.component.common.knowledge.util.Disposals;
import org.switchyard.component.common.knowledge.util.Environments;

/**
 * A "bpm" implementation of a KnowledgeExchangeHandler.
 *
 * @author David Ward &lt;<a href="mailto:dward@jboss.org">dward@jboss.org</a>&gt; &copy; 2012 Red Hat Inc.
 */
public class BPMExchangeHandler extends KnowledgeExchangeHandler<BPMComponentImplementationModel> {

    private static final KnowledgeAction DEFAULT_ACTION = new KnowledgeAction(null, BPMActionType.START_PROCESS);

    private final boolean _persistent;
    private final String _processId;
    private EntityManagerFactory _entityManagerFactory;

    /**
     * Constructs a new BPMExchangeHandler with the specified model and service domain.
     * @param model the specified model
     * @param domain the specified service domain
     */
    public BPMExchangeHandler(BPMComponentImplementationModel model, ServiceDomain domain) {
        super(model, domain);
        _persistent = model.isPersistent();
        _processId = model.getProcessId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        if (_persistent) {
            _entityManagerFactory = Persistence.createEntityManagerFactory("org.jbpm.persistence.jpa");
        }
        super.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        if (_entityManagerFactory != null) {
            Disposals.newDisposal(_entityManagerFactory).dispose();
            _entityManagerFactory = null;
        }
        super.stop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Properties getPropertyOverrides() {
        // NOTE: not necessary any more, per mproctor
        /*
        Properties props = new Properties();
        props.setProperty("drools.processInstanceManagerFactory", JPAProcessInstanceManagerFactory.class.getName());
        props.setProperty("drools.processSignalManagerFactory", JPASignalManagerFactory.class.getName());
        return props;
        */
        return super.getPropertyOverrides();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String, Object> getEnvironmentOverrides() {
        if (_persistent) {
            UserTransaction ut = AS7TransactionHelper.getUserTransaction();
            TransactionManager tm = AS7TransactionHelper.getTransactionManager();
            Map<String, Object> env = new HashMap<String, Object>();
            env.put(EnvironmentName.ENTITY_MANAGER_FACTORY, _entityManagerFactory);
            env.put(EnvironmentName.TRANSACTION, ut);
            env.put(EnvironmentName.TRANSACTION_MANAGER, new JtaTransactionManager(ut, null, tm));
            env.put(EnvironmentName.PERSISTENCE_CONTEXT_MANAGER, new JpaProcessPersistenceContextManager(Environments.getEnvironment(env)));
            return env;
        }
        return super.getEnvironmentOverrides();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public KnowledgeAction getDefaultAction() {
        return DEFAULT_ACTION;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleAction(Exchange exchange, KnowledgeAction action) throws HandlerException {
        Message outputMessage = null;
        AS7TransactionHelper utx = new AS7TransactionHelper(_persistent);
        ExchangePattern exchangePattern = exchange.getContract().getProviderOperation().getExchangePattern();
        BPMActionType actionType = (BPMActionType)action.getType();
        switch (actionType) {
            case START_PROCESS: {
                try {
                    utx.begin();
                    KnowledgeSession session = getBPMSession(exchange);
                    ProcessInstance processInstance = session.getStateful().startProcess(_processId, getInputMap(exchange, action));
                    if (ExchangePattern.IN_OUT.equals(exchangePattern)) {
                        outputMessage = handleOutput(exchange, action, session, processInstance);
                    }
                    utx.commit();
                } catch (RuntimeException re) {
                    utx.rollback();
                    throw re;
                }
                break;
            }
            case SIGNAL_EVENT: {
                try {
                    utx.begin();
                    KnowledgeSession session = getBPMSession(exchange);
                    Long processInstanceId = getProcessInstanceId(exchange);
                    Object signalEvent = getSignalEvent(exchange);
                    String signalId = getSignalId(exchange, action);
                    if (processInstanceId != null) {
                        session.getStateful().signalEvent(signalId, signalEvent, processInstanceId);
                    } else {
                        session.getStateful().signalEvent(signalId, signalEvent);
                    }
                    if (ExchangePattern.IN_OUT.equals(exchangePattern)) {
                        ProcessInstance processInstance = session.getStateful().getProcessInstance(processInstanceId);
                        outputMessage = handleOutput(exchange, action, session, processInstance);
                    }
                    utx.commit();
                } catch (RuntimeException re) {
                    utx.rollback();
                    throw re;
                }
                break;
            }
            case ABORT_PROCESS_INSTANCE: {
                try {
                    utx.begin();
                    KnowledgeSession session = getBPMSession(exchange);
                    Long processInstanceId = getProcessInstanceId(exchange);
                    session.getStateful().abortProcessInstance(processInstanceId);
                    if (ExchangePattern.IN_OUT.equals(exchangePattern)) {
                        outputMessage = handleOutput(exchange, action, session, null);
                    }
                    utx.commit();
                } catch (RuntimeException re) {
                    utx.rollback();
                    throw re;
                }
                break;
            }
            default: {
                throw new HandlerException("Unsupported action type: " + actionType);
            }
        }
        if (outputMessage != null) {
            exchange.send(outputMessage);
        }
    }

    private KnowledgeSession getBPMSession(Exchange exchange) {
        KnowledgeSession session;
        if (_persistent) {
            Integer sessionId = getInteger(exchange, BPMConstants.SESSION_ID_PROPERTY);
            session = getPersistentSession(sessionId);
        } else {
            session = getStatefulSession();
        }
        WorkItemHandlers.registerWorkItemHandlers(getModel(), getLoader(), session.getStateful(), getDomain());
        return session;
    }

    private Long getProcessInstanceId(Exchange exchange) {
        return getLong(exchange, BPMConstants.PROCESSS_INSTANCE_ID_PROPERTY);
    }

    private Object getSignalEvent(Exchange exchange) {
        Object signalEvent = getObject(exchange, BPMConstants.SIGNAL_EVENT_PROPERTY);
        if (signalEvent == null) {
            signalEvent = exchange.getMessage().getContent();
        }
        return signalEvent;
    }

    private String getSignalId(Exchange exchange, KnowledgeAction action) {
        String signalId = getString(exchange, BPMConstants.SIGNAL_ID_PROPERTY);
        if (signalId == null) {
            signalId = action.getId();
        }
        return signalId;
    }

    private Message handleOutput(Exchange exchange, KnowledgeAction action, KnowledgeSession session, ProcessInstance processInstance) {
        Message outputMessage = exchange.createMessage();
        Context context = exchange.getContext();
        Integer sessionId = session.getId();
        if (sessionId != null && sessionId.intValue() > 0) {
            context.setProperty(BPMConstants.SESSION_ID_PROPERTY, sessionId, Scope.EXCHANGE);
        }
        Map<String, Object> expressionVariables = new HashMap<String, Object>();
        if (processInstance != null) {
            long processInstanceId = processInstance.getId();
            if (processInstanceId > 0) {
                context.setProperty(BPMConstants.PROCESSS_INSTANCE_ID_PROPERTY, Long.valueOf(processInstanceId), Scope.EXCHANGE);
            }
            if (processInstance instanceof WorkflowProcessInstanceImpl) {
                Map<String, Object> processInstanceVariables = ((WorkflowProcessInstanceImpl)processInstance).getVariables();
                if (processInstanceVariables != null) {
                    expressionVariables.putAll(processInstanceVariables);
                }
            }
        }
        Map<String, Object> output = getOutputMap(exchange, action, expressionVariables);
        for (Entry<String, Object> entry : output.entrySet()) {
            Object value = entry.getValue();
            if (value != null) {
                String key = entry.getKey();
                if (KnowledgeConstants.CONTENT_OUTPUT.equals(key)) {
                    outputMessage.setContent(value);
                } else {
                    context.setProperty(key, value, Scope.EXCHANGE);
                }
            }
        }
        return outputMessage;
    }

}
