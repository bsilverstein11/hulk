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

package com.creditease.uav.healthmanager.newlog.http;

import java.util.ArrayList;
import java.util.Collections;
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

import com.creditease.agent.ConfigurationManager;
import com.creditease.agent.helpers.DataConvertHelper;
import com.creditease.agent.helpers.JSONHelper;
import com.creditease.agent.helpers.StringHelper;
import com.creditease.agent.http.api.UAVHttpMessage;
import com.creditease.agent.spi.AbstractHttpHandler;
import com.creditease.uav.elasticsearch.client.ESClient;
import com.creditease.uav.healthmanager.newlog.HMNewLogIndexMgr;

public class NewLogQueryHandler extends AbstractHttpHandler<UAVHttpMessage> {

    private ESClient client;

    private long timeout = 5000;

    private HMNewLogIndexMgr indexMgr;

    public NewLogQueryHandler(String cName, String feature) {
        super(cName, feature);

        client = (ESClient) this.getConfigManager().getComponent(this.feature, "ESClient");

        timeout = DataConvertHelper
                .toLong(this.getConfigManager().getFeatureConfiguration(this.feature, "es.query.timeout"), 5000);

        indexMgr = (HMNewLogIndexMgr) ConfigurationManager.getInstance().getComponent("newlogservice",
                "HMNewLogIndexMgr");
    }

    @Override
    public String getContextPath() {

        return "/newlog/q";
    }

    @Override
    public void handle(UAVHttpMessage data) {

        String cmd = data.getIntent();

        switch (cmd) {
            case "qContent":
                queryByLogContent(data);
                break;
        }
    }

    /**
     * ??????????????????
     * 
     * @param data
     */
    @SuppressWarnings("rawtypes")
    private void queryByLogContent(UAVHttpMessage data) {

        BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();

        /**
         * ??????????????????????????????
         */
        long startTime = DataConvertHelper.toLong(data.getRequest("stime"), -1);
        long endTime = DataConvertHelper.toLong(data.getRequest("etime"), -1);

        if (startTime > -1 && endTime > -1 && endTime >= startTime) {
            queryBuilder.must(QueryBuilders.rangeQuery("l_timestamp").gte(startTime).lte(endTime));
        }

        /**
         * ????????????????????? ???????????????sline???eline??????????????????
         */
        long startLine = DataConvertHelper.toLong(data.getRequest("sline"), -1);
        long endLine = DataConvertHelper.toLong(data.getRequest("eline"), -1);

        if (startLine > -1 && endLine > -1 && endLine > startLine) {
            queryBuilder.must(QueryBuilders.rangeQuery("l_num").gte(startLine).lt(endLine));
        }
        else if (startLine > -1) {
            queryBuilder.must(QueryBuilders.rangeQuery("l_num").gte(startLine));
        }
        else if (endLine > -1) {
            queryBuilder.must(QueryBuilders.rangeQuery("l_num").lt(endLine));
        }

        String appid = data.getRequest("appid");
        if (appid != null) {
            queryBuilder.must(QueryBuilders.termQuery("appid", appid));
        }

        String ipport = data.getRequest("ipport");
        if (ipport != null) {
            queryBuilder.must(QueryBuilders.termQuery("ipport", ipport));
        }

        /**
         * ????????????
         */
        // get logtype for search
        String logType = data.getRequest("logtype");

        /**
         * ??????Type???????????????ctn
         */
        if (logType.endsWith("_def")) {

            parseSearchCondition(queryBuilder, data);
        }
        /**
         * ???????????????
         */
        else {
            // TODO
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
    private void parseSearchCondition(BoolQueryBuilder queryBuilder, UAVHttpMessage data) {

        String content = data.getRequest("ctn");

        if (content == null) {
            return;
        }
        // ????????????????????????""??????,?????????matchphrase
        if (content.startsWith("\"") && content.endsWith("\"")) {
            // ???????????????""
            content = content.substring(1, content.length() - 1);
            queryBuilder.must(QueryBuilders.matchPhraseQuery("content", content));
            return;
        }

        boolean hasCompandSearch = (content.indexOf("+") > -1) ? true : false;

        if (hasCompandSearch == false) {
            if (content.indexOf("*") == -1) {
                queryBuilder.must(QueryBuilders.matchQuery("content", content));
            }
            else {
                queryBuilder.must(QueryBuilders.wildcardQuery("content.asstring", content));
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
                        orQueryBuilder.should(QueryBuilders.matchQuery("content", orKwd));
                    }
                    else {
                        orQueryBuilder.should(QueryBuilders.wildcardQuery("content.asstring", orKwd));
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
                            andQueryBuilder.must(QueryBuilders.matchQuery("content", andKwd));
                        }
                        else {
                            andQueryBuilder.must(QueryBuilders.wildcardQuery("content.asstring", andKwd));
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
            SortBuilder stimeSort = new FieldSortBuilder("l_timestamp");
            stimeSort.order(SortOrder.ASC);
            SortBuilder lnumSort = new FieldSortBuilder("l_num");
            lnumSort.order(SortOrder.ASC);
            sorts = new SortBuilder[] { stimeSort, lnumSort };
        }
        return sorts;
    }

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

        // ???????????????eline????????????????????????????????????????????????
        long startLine = DataConvertHelper.toLong(data.getRequest("sline"), -1);
        long endLine = DataConvertHelper.toLong(data.getRequest("eline"), -1);
        if (startLine == -1 && endLine > -1) {
            Collections.reverse(records);
        }

        data.putResponse("rs", JSONHelper.toString(records));
        // ???????????????
        data.putResponse("count", shits.getTotalHits() + "");
    }

    @SuppressWarnings("rawtypes")
    private SearchResponse query(UAVHttpMessage data, QueryBuilder queryBuilder, QueryBuilder postFilter,
            SortBuilder[] sorts) {

        String indexDate = data.getRequest("indexdate");
        String currentIndex;
        if (indexDate != null) {
            // ???????????????index
            currentIndex = this.indexMgr.getIndexByDate(indexDate);
        }
        else {
            // get current index
            currentIndex = this.indexMgr.getCurrentIndex();
        }

        // get logtype for search
        SearchRequestBuilder srb = null;
        if (StringHelper.isEmpty(data.getRequest("logtype"))) {

            srb = client.getClient().prepareSearch(currentIndex).setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        }
        else {
            String logType = data.getRequest("logtype").replace('.', '_');

            srb = client.getClient().prepareSearch(currentIndex).setTypes(logType)
                    .setSearchType(SearchType.DFS_QUERY_THEN_FETCH);
        }

        int from = DataConvertHelper.toInt(data.getRequest("from"), -1);
        int size = DataConvertHelper.toInt(data.getRequest("size"), -1);

        if (from != -1 && size != -1) {
            long startLine = DataConvertHelper.toLong(data.getRequest("sline"), -1);
            long endLine = DataConvertHelper.toLong(data.getRequest("eline"), -1);
            // ??????????????????endline????????????????????????????????????100????????????????????????????????????????????????????????????endline??????100???
            if (startLine == -1 && endLine > -1) {
                srb = srb.addSort("l_timestamp", SortOrder.DESC).addSort("l_num", SortOrder.DESC).setFrom(from)
                        .setSize(size);
            }
            else {
                srb = srb.setFrom(from).setSize(size);
            }
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
