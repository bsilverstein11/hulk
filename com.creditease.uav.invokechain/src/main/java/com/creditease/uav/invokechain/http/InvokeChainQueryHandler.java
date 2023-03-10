/*-
 * <<
 * UAVStack
 * ==
 * Copyright (C) 2016 - 2017 UAVStack
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */

package com.creditease.uav.invokechain.http;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortOrder;

import com.creditease.agent.helpers.DataConvertHelper;
import com.creditease.agent.helpers.JSONHelper;
import com.creditease.agent.helpers.StringHelper;
import com.creditease.agent.http.api.UAVHttpMessage;
import com.creditease.agent.spi.AbstractHttpHandler;
import com.creditease.uav.elasticsearch.client.ESClient;
import com.creditease.uav.invokechain.InvokeChainIndexMgr;

public class InvokeChainQueryHandler extends AbstractHttpHandler<UAVHttpMessage> {

    private ESClient client;

    private long timeout = 5000;

    private InvokeChainIndexMgr indexMgr;

    public InvokeChainQueryHandler(String cName, String feature) {
        super(cName, feature);
        client = (ESClient) this.getConfigManager().getComponent(this.feature, "ESClient");

        timeout = DataConvertHelper
                .toLong(this.getConfigManager().getFeatureConfiguration(this.feature, "es.query.timeout"), 5000);

        indexMgr = (InvokeChainIndexMgr) this.getConfigManager().getComponent(this.feature, "InvokeChainIndexMgr");
    }

    @Override
    public String getContextPath() {

        return "/ivc/q";
    }

    @Override
    public void handle(UAVHttpMessage data) {

        String cmd = data.getIntent();

        switch (cmd) {
            /**
             * ???????????????????????????????????????
             */
            case "qApp":
                queryByApp(data);
                break;
            /**
             * ??????trace id???????????????trace
             */
            case "qTrace":
                queryByTrace(data);
                break;
        }
    }

    /**
     * ??????trace id???????????????trace
     * 
     * @param data
     */
    @SuppressWarnings("rawtypes")
    private void queryByTrace(UAVHttpMessage data) {

        String traceid = data.getRequest("traceid");

        if (StringHelper.isEmpty(traceid)) {
            data.putResponse("rs", "ERR");
            data.putResponse("msg", "No TraceID Found");
            return;
        }

        QueryBuilder qb = QueryBuilders.termQuery("traceid", traceid);

        SortBuilder[] sorts = buildSort(data);

        String indexDate = getIndexDateFromTraceId(traceid);

        data.putRequest("indexdate", indexDate);

        queryToList(data, qb, null, sorts);
    }

    /**
     * \ ??????traceid?????????????????????ES?????????
     * 
     * @param traceid
     * @return indexData
     */
    private static String getIndexDateFromTraceId(String traceid) {

        int count = 0;
        StringBuilder sb = new StringBuilder();
        // ???traceid??????????????????,????????????'_'????????????'_'???????????????
        for (int i = 0; i < traceid.length(); i++) {
            if (count == 2 && traceid.charAt(i) != '_') {
                sb.append(traceid.charAt(i));
            }
            else if (count == 2) {
                break;
            }
            if (traceid.charAt(i) == '_') {
                count++;
            }
        }
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Long timeStamp = DataConvertHelper.toLong(sb.toString(), System.currentTimeMillis());
        String indexdate = dateFormat.format(timeStamp);
        return indexdate;
    }

    /**
     * ???????????????????????????????????????
     * 
     * @param data
     */
    @SuppressWarnings("rawtypes")
    private void queryByApp(UAVHttpMessage data) {

        long startTime = DataConvertHelper.toLong(data.getRequest("stime"), -1);
        long endTime = DataConvertHelper.toLong(data.getRequest("etime"), -1);

        if (startTime == -1 || endTime == -1 || endTime < startTime) {
            data.putResponse("rs", "ERR");
            data.putResponse("msg", "The Time Range Error: startTime=" + startTime + ",endTime=" + endTime);
            return;
        }

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        queryBuilder.must(QueryBuilders.rangeQuery("stime").gte(startTime).lte(endTime));

        String ipport = data.getRequest("ipport");

        if (ipport != null) {
            queryBuilder.must(QueryBuilders.matchQuery("ipport", ipport));
        }

        String appid = data.getRequest("appid");
        if (appid != null) {
            queryBuilder.must(QueryBuilders.matchQuery("appid", appid));
        }

        String appuuid = data.getRequest("appuuid");
        if (appuuid != null) {
            queryBuilder.must(QueryBuilders.termQuery("appuuid", appuuid));
        }

        String appgroup = data.getRequest("appgroup");
        if (appgroup != null) {
            queryBuilder.must(QueryBuilders.matchQuery("appgroup", appgroup));
        }

        String eptype = data.getRequest("eptype");
        if (eptype != null) {

            BoolQueryBuilder typeQuery = QueryBuilders.boolQuery();

            String[] types = eptype.split(",");

            for (String type : types) {
                typeQuery.should(QueryBuilders.matchQuery("eptype", type));
            }

            queryBuilder.must(typeQuery);
        }

        String cls = data.getRequest("class");

        if (cls != null) {
            parseSearchCondition(queryBuilder, cls, "class");
        }

        String method = data.getRequest("method");

        if (method != null) {
            parseSearchCondition(queryBuilder, method, "method");
        }

        String url = data.getRequest("url");

        if (url != null) {
            parseSearchCondition(queryBuilder, url, "url");
        }
        
        String state = data.getRequest("state");

        if (state != null) {
            parseSearchCondition(queryBuilder, state, "state");
        }

        SortBuilder[] sorts = buildSort(data);

        this.queryToList(data, queryBuilder, null, sorts);
    }
    
    /**
     * parseSearchCondition
     * 
     * ???????????????????????????????????????????????????????????????
     * 
     * ???????????????????????????+????????????????????????
     * 
     * ???????????????????????????????????????
     * 
     * ????????? <kwd1>+<kwd2> <kwd3> <kwd4>
     * 
     * ???????????????????????????kwd1 and kwd2 ??????????????????kwd3?????????kwd4??????
     * 
     * @param queryBuilder
     * @param content
     */
    private void parseSearchCondition(BoolQueryBuilder queryBuilder, String content, String field) {

        if (content == null) {
            return;
        }
        
        boolean hasCompandSearch = (content.indexOf("+") > -1) ? true : false;

        if (hasCompandSearch == false) {
            if (content.indexOf("*") == -1) {
                queryBuilder.must(QueryBuilders.matchQuery(field, content));
            }
            else {
                queryBuilder.must(QueryBuilders.wildcardQuery(field+".keyword", content));
            }
        }
        else {

            BoolQueryBuilder orQueryBuilder = QueryBuilders.boolQuery();

            String[] orKwds = content.split(" ");

            for (String orKwd : orKwds) {

                /**
                 * ??????????????????????????????????????????*
                 */
                if (orKwd.indexOf("+") == -1) {
                    if (orKwd.indexOf("*") == -1) {
                        orQueryBuilder.should(QueryBuilders.matchQuery(field, orKwd));
                    }
                    else {
                        orQueryBuilder.should(QueryBuilders.wildcardQuery(field+".keyword", orKwd));
                    }
                }
                else {
                    /**
                     * ???????????????????????????*
                     */
                    String[] andKwds = orKwd.split("\\+");

                    BoolQueryBuilder andQueryBuilder = QueryBuilders.boolQuery();

                    for (String andKwd : andKwds) {
                        if (andKwd.indexOf("*") == -1) {
                            andQueryBuilder.must(QueryBuilders.matchQuery(field, andKwd));
                        }
                        else {
                            andQueryBuilder.must(QueryBuilders.wildcardQuery(field+".keyword", andKwd));
                        }
                    }

                    orQueryBuilder.should(andQueryBuilder);
                }

            }

            queryBuilder.must(orQueryBuilder);
        }
    }

    @SuppressWarnings("rawtypes")
    private SortBuilder[] buildSort(UAVHttpMessage data) {

        SortBuilder[] sorts = null;

        String sort = data.getRequest("sort");

        if (sort != null) {
            String[] sortFieldStrs = sort.split(",");
            List<SortBuilder> ls = new ArrayList<SortBuilder>();
            for (String sortFieldStr : sortFieldStrs) {
                String[] sortExp = sortFieldStr.split("=");
                SortBuilder stimeSort = new FieldSortBuilder(sortExp[0]);
                stimeSort.order(SortOrder.fromString(sortExp[1]));
                ls.add(stimeSort);
            }
            sorts = new SortBuilder[ls.size()];
            sorts = ls.toArray(sorts);
        }
        else {
            SortBuilder stimeSort = new FieldSortBuilder("stime");
            stimeSort.order(SortOrder.DESC);
            sorts = new SortBuilder[] { stimeSort };
        }
        return sorts;
    }

    /**
     * 
     * @param data
     * @param queryBuilder
     * @param postFilter
     */
    @SuppressWarnings("rawtypes")
    private void queryToList(UAVHttpMessage data, QueryBuilder queryBuilder, QueryBuilder postFilter,
            SortBuilder[] sorts) {

        SearchResponse sr = query(data, queryBuilder, postFilter, sorts);

        SearchHits shits = sr.getHits();

        List<Map<String, Object>> records = new ArrayList<Map<String, Object>>();

        for (SearchHit sh : shits) {
            Map<String, Object> record = sh.getSourceAsMap();

            if (record == null) {
                continue;
            }

            records.add(record);
        }

        data.putResponse("rs", JSONHelper.toString(records));
        // ??????????????????
        data.putResponse("count", shits.getTotalHits() + "");
    }

    /**
     * 
     * @param data
     * @param queryBuilder
     * @param postFilter
     * @return
     */
    @SuppressWarnings("rawtypes")
    private SearchResponse query(UAVHttpMessage data, QueryBuilder queryBuilder, QueryBuilder postFilter,
            SortBuilder[] sorts) {

        String date = data.getRequest("indexdate");
        String currentIndex;
        if (date != null) {
            // ??????index
            currentIndex = this.indexMgr.getIndexByDate(date);
        }
        else {
            // current index
            currentIndex = this.indexMgr.getCurrentIndex();
        }

        SearchRequestBuilder srb = client.getClient().prepareSearch(currentIndex)
                .setTypes(InvokeChainIndexMgr.IVC_Table).setSearchType(SearchType.DFS_QUERY_THEN_FETCH);

        int from = DataConvertHelper.toInt(data.getRequest("from"), -1);
        int size = DataConvertHelper.toInt(data.getRequest("size"), -1);

        if (from != -1 && size != -1) {
            srb = srb.setFrom(from).setSize(size);
        }

        srb.setQuery(queryBuilder);

        if (postFilter != null) {
            srb.setPostFilter(postFilter);
        }

        if (sorts != null && sorts.length > 0) {
            for (SortBuilder sb : sorts) {
                srb.addSort(sb);
            }
        }

        SearchResponse sr = srb.get(TimeValue.timeValueMillis(timeout));

        return sr;
    }

}
