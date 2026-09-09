package com.nextdigital.oncall.audit;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.persistence.EntityManagerFactory;

/**
 * Fuerza a que el EntityManagerFactory se construya después de
 * {@code auditHibernateConfig}, de modo que el listener de auditoría esté
 * publicado en {@link AuditEventEngineContributor} cuando Hibernate cree la
 * EventEngine (el SPI se descubre por ServiceLoader y no puede depender de
 * Spring).
 */
@Component
public class AuditEntityManagerFactoryDependsOnPostProcessor implements BeanFactoryPostProcessor, Ordered {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        for (String beanName : beanFactory.getBeanNamesForType(EntityManagerFactory.class, true, false)) {
            BeanDefinition definition = beanFactory.getBeanDefinition(beanName);
            definition.setDependsOn(
                    StringUtils.addStringToArray(definition.getDependsOn(), "auditHibernateConfig"));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
