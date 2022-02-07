package com.codingmore.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.codingmore.dto.PostTagParam;
import com.codingmore.dto.PostsPageQueryParam;
import com.codingmore.dto.PostsParam;
import com.codingmore.model.*;
import com.codingmore.mapper.PostsMapper;
import com.codingmore.service.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.codingmore.util.TermRelationType;
import com.codingmore.vo.PostsVo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * <p>
 * 文章 服务实现类
 * </p>
 *
 * @author 石磊
 * @since 2021-09-12
 */
@Service
public class PostsServiceImpl extends ServiceImpl<PostsMapper, Posts> implements IPostsService {
    @Autowired
    private ITermRelationshipsService iTermRelationshipsService;
    @Autowired
    private IPostTagService iPostTagService;
    @Autowired
    private IPostTagRelationService iPostTagRelationService;

    @Override
    @Transactional(propagation = Propagation.REQUIRED,rollbackFor = Exception.class)
    public boolean savePosts(PostsParam postsParam) {
        Posts posts = new Posts();
        BeanUtils.copyProperties(postsParam,posts);
        try {
            handleAttribute(postsParam,posts);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        posts.setCommentCount(0L);
        if(posts.getPostDate() == null){
            posts.setPostDate(new Date());
        }
        this.save(posts);
        ObjectUtil.clone(posts);
        if(StringUtils.isNotBlank(postsParam.getTags())){
            String[] tags = postsParam.getTags().split(",");
            for(String tag:tags){
                QueryWrapper<PostTag> postTagQueryWrapper = new QueryWrapper<>();
                postTagQueryWrapper.eq("description",tag);
                List<PostTag>  tagList = iPostTagService.list(postTagQueryWrapper);
                if(tagList.size() == 0){
                    PostTagParam postTagParam = new PostTagParam();
                    postTagParam.setObjectId(posts.getId());
                    postTagParam.setDescription(tag);
                    // TODO: 2021/11/14 先默认 循环添加
                    postTagParam.setTermOrder(0);
                    iPostTagService.savePostTag(postTagParam);
                }else{
                    PostTagRelation postTagRelation = new PostTagRelation();
                    postTagRelation.setPostTagId(tagList.get(0).getPostId());
                    postTagRelation.setObjectId(posts.getId());
                    postTagRelation.setTermOrder(0);
                    iPostTagRelationService.save(postTagRelation);
                }
            }
        }

        return insertTermRelationships(postsParam,posts);

    }



    @Override
    public boolean updatePosts(PostsParam postsParam) {
        Posts posts = this.getById(postsParam.getId());
        Date publishDate = posts.getPostDate();
        BeanUtils.copyProperties(postsParam,posts);
        try {
            handleAttribute(postsParam,posts);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        //防止修改发布时间
        posts.setPostDate(publishDate);
        posts.setPostModified(new Date());
        this.updateById(posts);
        QueryWrapper<TermRelationships> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("object_id",postsParam.getId());
        queryWrapper.eq("term_taxonomy_id",postsParam.getTermTaxonomyId());
        int count = iTermRelationshipsService.count(queryWrapper);
        // 关系不能重复
        if(count ==0){
            return insertTermRelationships(postsParam,posts);
        }

        return true;
    }

    @Override
    public boolean removePostsById(Long id) {
        this.removeById(id);
        QueryWrapper<TermRelationships> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("object_id",id);

        return  iTermRelationshipsService.remove(queryWrapper);
    }

    @Override
    public PostsVo getPostsById(Long id) {
        Posts posts = this.getById(id);
        PostsVo postsVo = new PostsVo();
        BeanUtils.copyProperties(posts,postsVo);
        QueryWrapper<TermRelationships> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("object_id",posts.getId());
        List<TermRelationships> termRelationshipsList = iTermRelationshipsService.list(queryWrapper);
        if(termRelationshipsList.size()>0){
            postsVo.setTermTaxonomyId(termRelationshipsList.get(0).getTermTaxonomyId());
        }
        return postsVo;
    }

    @Override
    public IPage<PostsVo> findByPage(PostsPageQueryParam postsPageQueryParam) {
        QueryWrapper<PostsPageQueryParam> queryWrapper = new QueryWrapper<>();
        if(postsPageQueryParam.getPostType()!=null){
            queryWrapper.eq("a.post_type",postsPageQueryParam.getPostType().toString());
        }
        if(postsPageQueryParam.getTermTaxonomyId()!=null){
            queryWrapper.eq("b.term_taxonomy_id",postsPageQueryParam.getTermTaxonomyId());
        }
        Page<PostsVo> postsPage = new Page<>(postsPageQueryParam.getPage(), postsPageQueryParam.getPageSize());

        return this.getBaseMapper().findByPage(postsPage,queryWrapper);
    }

    private boolean insertTermRelationships(PostsParam postsParam, Posts posts){
        TermRelationships termRelationships  = new TermRelationships();
        termRelationships.setTermTaxonomyId(postsParam.getTermTaxonomyId());
        termRelationships.setObjectId(posts.getId());
        termRelationships.setTermOrder(postsParam.getMenuOrder());
        termRelationships.setType(TermRelationType.CONTENT.getType());
        return iTermRelationshipsService.save(termRelationships);
    }

    /**
     * 处理扩展字段
     */
    private void handleAttribute(PostsParam postsParam, Posts posts) throws JsonProcessingException {
        if (StringUtils.isNotBlank(postsParam.getAttribute())) {
            Map attribute = objectMapper.readValue(postsParam.getAttribute(), Map.class);
            posts.setAttribute(attribute);
        }
    }
    private static ObjectMapper objectMapper = new ObjectMapper();
}
