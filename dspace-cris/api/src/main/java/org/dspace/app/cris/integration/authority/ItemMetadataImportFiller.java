/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * https://github.com/CILEA/dspace-cris/wiki/License
 */
package org.dspace.app.cris.integration.authority;

import java.beans.PropertyEditor;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.cris.integration.authority.ItemMetadataImportFillerConfiguration.MappingDetails;
import org.dspace.app.cris.integration.authority.ItemMetadataImportFillerConfiguration.MetricsMappingDetails;
import org.dspace.app.cris.metrics.common.model.CrisMetrics;
import org.dspace.app.cris.metrics.common.services.MetricsPersistenceService;
import org.dspace.app.cris.model.ACrisObject;
import org.dspace.app.cris.model.ResearchObject;
import org.dspace.app.cris.service.ApplicationService;
import org.dspace.app.cris.util.ResearcherPageUtils;
import org.dspace.content.IMetadataValue;
import org.dspace.content.Item;
import org.dspace.content.MetadataValue;
import org.dspace.core.Context;
import org.dspace.core.LogManager;

import it.cilea.osd.jdyna.model.Property;

public class ItemMetadataImportFiller implements ImportAuthorityFiller
{
    private static final Logger log = Logger
            .getLogger(ItemMetadataImportFiller.class);

    private boolean allowsUpdateByDefault = false;

    private Map<String, ItemMetadataImportFillerConfiguration> configurations;

    protected ApplicationService applicationService;

    private MetricsPersistenceService metricsPersistenceService;

    public void setApplicationService(ApplicationService applicationService)
    {
        this.applicationService = applicationService;
    }

    public void setConfigurations(
            Map<String, ItemMetadataImportFillerConfiguration> configurations)
    {
        this.configurations = configurations;
    }

    public void setAllowsUpdateByDefault(boolean allowsUpdateByDefault)
    {
        this.allowsUpdateByDefault = allowsUpdateByDefault;
    }

    @Override
    public boolean allowsUpdate(Context ctx, Item item,
            List<IMetadataValue> MetadataValueList, String authorityKey, ACrisObject rp)
    {
        if (MetadataValueList != null && MetadataValueList.size() > 0)
        {
            IMetadataValue m = MetadataValueList.get(0);
            String mdString = m.getSchema() + "." + m.getElement()
                    + (m.getQualifier() != null ? "." + m.getQualifier() : "");
            ItemMetadataImportFillerConfiguration conf = configurations
                    .get(mdString);
            if (conf != null && conf.getUpdateEnabled() != null)
            {
                return conf.getUpdateEnabled();
            }
            else if (conf == null)
            {
                return false;
            }
        }
        return allowsUpdateByDefault;
    }

    @Override
    public void fillRecord(Context context, Item item, List<IMetadataValue> metadata,
            String hashedValue, ACrisObject crisObject)
    {
        int idx = 0;
        for (IMetadataValue m : metadata)
        {
            String mdString = m.getSchema() + "." + m.getElement()
                    + (m.getQualifier() != null ? "." + m.getQualifier() : "");
            log.debug("fillRecord -> " + mdString);
            log.debug("fillRecord -> " + crisObject.getAnagrafica4view());
            ItemMetadataImportFillerConfiguration conf = configurations
                    .get(mdString);
            
            if (conf != null)
            {
                String prefix = "";
                if (crisObject instanceof ResearchObject)
                {
                    prefix = ((ResearchObject) crisObject).getTypo()
                            .getShortName();
                }
                
                Set<String> alreadyDeleted = new HashSet<String>();
                for (Entry<String, MappingDetails> entry : conf.getMapping()
                        .entrySet())
                {
                    String mdInput = entry.getKey();
                    MappingDetails details = entry.getValue();                    
                    if (!(details instanceof MetricsMappingDetails)) {

                        log.debug("fillRecord -> conf to delete -> " + mdInput);
                        String detailsShortname = prefix + details.getShortName();
                        log.debug("fillRecord -> conf to delete -> " + detailsShortname);

                        if (!details.isAppendMode())
                        {
                            if (!alreadyDeleted.contains(detailsShortname))
                            {
                                ResearcherPageUtils
                                        .cleanPropertyByPropertyDefinition(
                                                crisObject, detailsShortname);
                                alreadyDeleted.add(detailsShortname);
                            }
                        }
                    }                    
                }

                
                String pdefKey = prefix + crisObject.getMetadataFieldTitle();
                ResearcherPageUtils.cleanPropertyByPropertyDefinition(crisObject, pdefKey);
                applicationService.saveOrUpdate(crisObject.getCRISTargetClass(), crisObject);
                ResearcherPageUtils.buildTextValue(crisObject,m.getValue(),pdefKey);
                
                for (Entry<String, MappingDetails> entry : conf.getMapping()
                        .entrySet())
                {
                    String mdInput = entry.getKey();
                    MappingDetails details = entry.getValue();
                    if (details instanceof MetricsMappingDetails)
                    {
                        MetricsMappingDetails mmd = (MetricsMappingDetails)details;
                        buildMetric(context, item, crisObject, m, mdInput, mmd, metricsPersistenceService);
                    }
                    
                    log.debug("fillRecord -> conf -> " + mdInput);
                    log.debug(
                            "fillRecord -> conf -> " + details.getShortName());
                    List<? extends Property> props = (List<? extends Property>) crisObject
                            .getAnagrafica4view().get(details.getShortName());
                    List<IMetadataValue> inputs = item.getItemService()
                            .getMetadataByMetadataString(item, mdInput);
                    if (details.isUseAll())
                    {
                        for (IMetadataValue value : inputs)
                        {
                            Object dcvalue = buildGenericValue(context, item,
                                    value, details);
                            if(dcvalue!= null && dcvalue.equals(MetadataValue.PARENT_PLACEHOLDER_VALUE)){
                            	continue;
                            }
                            if (!containsValue(props, dcvalue))
                            {
                                ResearcherPageUtils.buildGenericValue(
                                        crisObject, dcvalue,
                                        prefix + details.getShortName(),
                                        details.getVisibility());
                            }
                        }
                    }
                    else
                    {
                        try
                        {
                            IMetadataValue value = null;
                            try {
                                value = inputs.get(m.getPlace());
                            }
                            catch (Exception ex) {
                            	if (inputs.size() > idx) {
                            		value = inputs.get(idx);
                            	} else {
                            		continue;
                            	}
                            }
                            Object dcvalue = buildGenericValue(context, item,
                                    value, details);
                            if(dcvalue!= null && dcvalue.equals(MetadataValue.PARENT_PLACEHOLDER_VALUE)){
                            	continue;
                            }
                            if (!containsValue(props, dcvalue))
                            {
                                ResearcherPageUtils.buildGenericValue(
                                        crisObject, dcvalue,
                                        prefix + details.getShortName(),
                                        details.getVisibility());
                            }
                        }
                        catch (IndexOutOfBoundsException ex)
                        {
                            log.error(LogManager.getHeader(context,
                                    "fillRecord",
                                    "missing " + mdInput + " for position "
                                            + idx + " in item "
                                            + item.getID()));
                        }
                    }
                }
                idx++;
            }
        }
    }

    public static void buildMetric(Context context, Item item,
            ACrisObject crisObject, IMetadataValue m, String mdInput,
            MetricsMappingDetails mmd, MetricsPersistenceService metricService)
    {
        List<IMetadataValue> mm = item.getItemService().getMetadataByMetadataString(item, mdInput);
        if (mm != null && mm.size() > 0)
        {
            try
            {
                CrisMetrics metric = new CrisMetrics();
                
                mmd.computeMetricCount(m.getPlace(), mm, item, metric);
                
                if (crisObject != null)
                {
                    metric.setResourceId(crisObject.getID());
                    metric.setResourceTypeId(crisObject.getType());
                    metric.setUuid(crisObject.getHandle());
                }
                else
                {
                    metric.setResourceId(item.getID());
                    metric.setResourceTypeId(item.getType());
                    metric.setUuid(item.getHandle());
                }

                Date start = null;
                Date end = null;
                if (StringUtils.isNotBlank(mmd.getRangeByYear()))
                {
                    String acquisitionYear = item.getItemService().getMetadata(item, mmd.getRangeByYear());

                    int year = -1;
                    if (StringUtils.isNotBlank(acquisitionYear))
                    {
                        year = Integer.parseInt(acquisitionYear);
                    }
                    else
                    {
                        // get a calendar using the default time zone
                        // and
                        // locale.
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(new Date());
                        year = calendar.get(Calendar.YEAR);
                    }

                    Calendar cal = Calendar.getInstance();
                    cal.set(Calendar.YEAR, year);
                    cal.set(Calendar.DAY_OF_YEAR, 1);
                    cal.set(Calendar.HOUR_OF_DAY, 0);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);
                    start = cal.getTime();

                    // set date to last day of year
                    cal.set(Calendar.YEAR, year);
                    cal.set(Calendar.MONTH, 11); // 11 = december
                    cal.set(Calendar.DAY_OF_MONTH, 31); // new years eve
                    cal.set(Calendar.HOUR_OF_DAY, 23);
                    cal.set(Calendar.MINUTE, 59);
                    cal.set(Calendar.SECOND, 59);
                    cal.set(Calendar.MILLISECOND, 999);
                    end = cal.getTime();
                }
                else
                {
                    if (StringUtils.isNotBlank(mmd.getStartDate())
                            && StringUtils.isNotBlank(mmd.getEndDate()))
                    {
                        start = (Date) formatAsDate(context, item,
                                item.getItemService().getMetadataByMetadataString(item,
                                        mmd.getStartDate()).get(0));
                        end = (Date) formatAsDate(context, item, item.getItemService().getMetadataByMetadataString(item,mmd.getEndDate()).get(0));
                    }
                }
                metric.setStartDate(start);
                metric.setEndDate(end);
                metricService.saveOrUpdate(CrisMetrics.class, metric);
            }
            catch (Exception e)
            {
                log.warn("Errors in creation of CRIS_METRICS for " + mdInput, e);
            }
        }
    }

    private Object buildGenericValue(Context context, Item item,
            IMetadataValue value, MappingDetails details)
    {
        Object dcvalue = value.getValue();
        if (details.formatAsDate())
        {
            dcvalue = formatAsDate(context, item, value);
        }
        else if (value.getAuthority() != null)
        {
            dcvalue = applicationService.getEntityByCrisId(value.getAuthority());
        }
        else if (details.isFormatAsInteger()) {
            if(StringUtils.isNotBlank(value.getValue())) {
                dcvalue = Integer.parseInt(value.getValue());
            }
        }
        return dcvalue;
    }

    private static Object formatAsDate(Context context, Item item, IMetadataValue value)
    {
        Object dcvalue;
        SimpleDateFormat sdf;
        if (value.getValue().length() == 4)
        {
            sdf = new SimpleDateFormat("yyyy");
        }
        else if (value.getValue().length() == 7)
        {
            sdf = new SimpleDateFormat("yyyy-MM");
        }
        else
        {
            sdf = new SimpleDateFormat("yyyy-MM-dd");
        }
        try
        {
            dcvalue = sdf.parse(value.getValue());
        }
        catch (ParseException e)
        {
            log.warn(
                    LogManager
                            .getHeader(context, "fillRecord",
                                    "Mangled date value " + value.getValue()
                                            + " item_id=" + item.getID()),
                    e);
            dcvalue = null;
        }
        return dcvalue;
    }

    private boolean containsValue(List<? extends Property> props, Object val)
    {
        for (Property p : props)
        {
            PropertyEditor pe1 = p.getTypo().getRendering().getPropertyEditor(applicationService);
            PropertyEditor pe2 = p.getTypo().getRendering().getPropertyEditor(applicationService);
            pe1.setValue(p.getValue().getObject());
            pe2.setValue(val);
            if (pe1.getAsText().equals(pe2.getAsText()))
            {
                return true;
            }
        }
        return false;
    }

    public void setMetricsPersistenceService(MetricsPersistenceService metricsPersistenceService)
    {
        this.metricsPersistenceService = metricsPersistenceService;
    }
    
}
