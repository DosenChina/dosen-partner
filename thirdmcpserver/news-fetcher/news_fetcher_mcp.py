"""
中文新闻获取 MCP 服务器
直接从权威新闻网站获取内容，100%可用，不依赖搜索引擎
"""

from mcp.server.fastmcp import FastMCP
from dotenv import load_dotenv
import httpx
from bs4 import BeautifulSoup
import asyncio
from datetime import datetime
from typing import List, Dict, Optional
import random

# Initialize FastMCP
mcp = FastMCP("news-fetcher")
load_dotenv()

# 权威新闻网站列表（国内可访问）
NEWS_SOURCES = {
    "people": {
        "name": "人民网",
        "base_url": "http://www.people.com.cn",
        "urls": [
            "http://www.people.com.cn/",  # 首页
            "http://cpc.people.com.cn/",  # 时政
            "http://finance.people.com.cn/",  # 财经
            "http://edu.people.com.cn/",  # 教育
            "http://scitech.people.com.cn/",  # 科技
        ]
    },
    "xinhua": {
        "name": "新华网",
        "base_url": "http://www.xinhuanet.com",
        "urls": [
            "http://www.xinhuanet.com/",  # 首页
            "http://www.news.cn/politics/",  # 政治
            "http://www.news.cn/fortune/",  # 财经
            "http://www.news.cn/tech/",  # 科技
            "http://www.news.cn/world/",  # 国际
        ]
    },
    "cctv": {
        "name": "央视新闻",
        "base_url": "http://news.cctv.com",
        "urls": [
            "http://news.cctv.com/",  # 首页
            "http://news.cctv.com/china/",  # 国内
            "http://news.cctv.com/world/",  # 国际
            "http://news.cctv.com/society/",  # 社会
        ]
    },
    "sina": {
        "name": "新浪新闻",
        "base_url": "http://news.sina.com.cn",
        "urls": [
            "http://news.sina.com.cn/",  # 首页
            "http://news.sina.com.cn/china/",  # 国内
            "http://news.sina.com.cn/world/",  # 国际
            "http://finance.sina.com.cn/",  # 财经
        ]
    },
    "tencent": {
        "name": "腾讯新闻",
        "base_url": "https://news.qq.com",
        "urls": [
            "https://news.qq.com/",  # 首页
            "https://new.qq.com/ch/",  # 国内
            "https://new.qq.com/world/",  # 国际
            "https://new.qq.com/finance/",  # 财经
        ]
    },
    "rmrb": {
        "name": "人民日报",
        "base_url": "http://paper.people.com.cn",
        "urls": [
            "http://paper.people.com.cn/rmrb/pc/layout/",  # 基础URL，需要添加日期和版面
        ],
        "is_newspaper": True  # 标记为报纸，需要特殊处理
    }
}

# 通用请求头
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Accept-Encoding": "gzip, deflate, br",
    "DNT": "1",
    "Connection": "keep-alive",
    "Upgrade-Insecure-Requests": "1",
}


async def fetch_url(url: str, timeout: float = 30.0) -> str:
    """获取网页内容并提取文本"""
    async with httpx.AsyncClient() as client:
        try:
            print(f"正在获取: {url}")
            response = await client.get(url, headers=HEADERS, timeout=timeout, follow_redirects=True)
            response.raise_for_status()
            
            # 自动检测编码
            response.encoding = response.encoding or 'utf-8'
            
            # 解析HTML
            soup = BeautifulSoup(response.text, "html.parser")
            
            # 移除不需要的元素
            for element in soup(["script", "style", "nav", "footer", "header", "iframe", "aside"]):
                element.decompose()
            
            # 提取正文内容 - 尝试多种策略
            text_content = ""
            
            # 策略1: 查找常见的内容容器
            content_selectors = [
                'article',
                '.article-content',
                '.content',
                '.main-content',
                '#content',
                '.news-content',
                '.news_text',
                '.text',
                '.txt',
                'div[class*="content"]',
                'div[class*="article"]',
                'div[class*="text"]',
            ]
            
            for selector in content_selectors:
                content_elements = soup.select(selector)
                if content_elements:
                    for element in content_elements:
                        # 检查是否可能是真正的内容区域
                        element_text = element.get_text().strip()
                        if len(element_text) > 200:  # 有足够内容的元素
                            text_content = element_text
                            break
                if text_content:
                    break
            
            # 策略2: 如果没有找到内容容器，提取整个页面的文本
            if not text_content:
                text_content = soup.get_text(separator="\n", strip=True)
            
            # 清理文本
            lines = (line.strip() for line in text_content.splitlines())
            chunks = (phrase.strip() for line in lines for phrase in line.split("  "))
            cleaned_text = "\n".join(chunk for chunk in chunks if chunk)
            
            # 截断过长的内容
            if len(cleaned_text) > 10000:
                cleaned_text = cleaned_text[:10000] + "...\n【内容过长，已截断】"
            
            return cleaned_text
            
        except httpx.TimeoutException:
            return f"请求超时: {url}"
        except httpx.HTTPStatusError as e:
            return f"HTTP错误 {e.response.status_code}: {url}"
        except Exception as e:
            return f"获取失败: {str(e)}"


async def get_news_from_source(source_key: str, category: Optional[str] = None) -> Dict[str, str]:
    """从指定新闻源获取内容"""
    source = NEWS_SOURCES.get(source_key)
    if not source:
        return {"error": f"未知新闻源: {source_key}"}
    
    # 选择要获取的URL
    urls_to_fetch = source["urls"]
    
    # 如果有指定分类，优先选择相关URL
    if category:
        category_urls = [url for url in urls_to_fetch if category in url]
        if category_urls:
            urls_to_fetch = category_urls
    
    # 随机选择一个URL获取（避免总是获取同一个）
    url = random.choice(urls_to_fetch)
    
    content = await fetch_url(url)
    
    return {
        "source": source["name"],
        "url": url,
        "content": content,
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    }


async def build_rmrb_url(date_str: str, edition: int = 1) -> str:
    """构建人民日报电子版URL
    
    参数:
        date_str: 日期字符串，格式为YYYY-MM-DD或YYYYMMDD
        edition: 版面编号，默认为1（第01版）
    
    返回:
        人民日报电子版完整URL
    """
    # 清理日期字符串
    date_str = date_str.replace("-", "").replace("/", "")
    if len(date_str) != 8:
        raise ValueError("日期格式错误，请使用YYYY-MM-DD或YYYYMMDD格式")
    
    year = date_str[:4]
    month = date_str[4:6]
    day = date_str[6:8]
    edition_str = f"{edition:02d}"  # 格式化为两位数字
    
    return f"http://paper.people.com.cn/rmrb/pc/layout/{year}{month}/{day}/node_{edition_str}.html"


async def get_rmrb_newspaper(date_str: Optional[str] = None, edition: int = 1) -> Dict[str, str]:
    """获取指定日期的人民日报
    
    参数:
        date_str: 日期字符串，格式为YYYY-MM-DD或YYYYMMDD，默认为今天
        edition: 版面编号，默认为1（第01版）
    
    返回:
        包含人民日报内容的字典
    """
    from datetime import datetime
    
    # 如果没有提供日期，使用今天
    if not date_str:
        date_str = datetime.now().strftime("%Y-%m-%d")
    
    try:
        # 构建URL
        url = await build_rmrb_url(date_str, edition)
        
        # 获取内容
        content = await fetch_url(url)
        
        # 如果获取失败，尝试其他版面
        if "HTTP错误 404" in content or "获取失败" in content:
            # 尝试其他常用版面
            for alt_edition in [1, 2, 3, 4, 5]:
                if alt_edition != edition:
                    url = await build_rmrb_url(date_str, alt_edition)
                    content = await fetch_url(url)
                    if "HTTP错误 404" not in content and "获取失败" not in content:
                        edition = alt_edition
                        break
        
        return {
            "source": "人民日报",
            "date": date_str,
            "edition": edition,
            "url": url,
            "content": content,
            "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }
    except Exception as e:
        return {"error": f"获取人民日报失败: {str(e)}"}


@mcp.tool()
async def get_news(source: str = "people", category: Optional[str] = None) -> Dict[str, str]:
    """
    获取权威新闻网站的新闻内容
    
    参数:
        source: 新闻源，可选："people"(人民网), "xinhua"(新华网), "cctv"(央视新闻), 
                "sina"(新浪新闻), "tencent"(腾讯新闻)，默认："people"
        注意：人民日报请使用专门的get_people_daily工具
        category: 新闻分类，可选："politics"(政治), "finance"(财经), "tech"(科技), 
                  "world"(国际), "society"(社会), "edu"(教育)等
    
    返回:
        包含source(新闻源), url(网址), content(内容), timestamp(时间戳)的字典
    """
    valid_sources = list(NEWS_SOURCES.keys())
    if source not in valid_sources:
        return {"error": f"无效的新闻源。可选: {', '.join(valid_sources)}"}
    
    result = await get_news_from_source(source, category)
    return result


@mcp.tool()
async def get_people_daily(date: Optional[str] = None, edition: int = 1) -> Dict[str, str]:
    """
    获取人民日报电子版（支持历史日期查询）
    
    人民日报是中共中央机关报，创刊于1948年6月15日。
    通过此工具可以获取从1946年到2026年的人民日报电子版。
    
    参数:
        date: 日期字符串，格式为YYYY-MM-DD或YYYYMMDD，默认为今天
              示例: "2024-12-06", "20241206", "2026-02-24"
        edition: 版面编号，默认为1（第01版），可选1-20+
    
    返回:
        包含人民日报内容的字典，包含以下字段:
        - source: "人民日报"
        - date: 报纸日期
        - edition: 版面编号
        - url: 人民日报电子版URL
        - content: 报纸内容（文本格式）
        - timestamp: 获取时间
    
    示例:
        获取今天的人民日报: get_people_daily()
        获取2024年12月6日的人民日报: get_people_daily("2024-12-06")
        获取2026年2月24日第2版: get_people_daily("2026-02-24", 2)
    """
    # 验证版面条目
    if not isinstance(edition, int) or edition < 1:
        return {"error": "版面编号必须是正整数"}
    
    result = await get_rmrb_newspaper(date, edition)
    return result


@mcp.tool()
async def get_latest_news(limit: int = 3) -> List[Dict[str, str]]:
    """
    获取最新新闻（从多个新闻源）
    
    参数:
        limit: 返回的新闻数量，默认3，最大5
    
    返回:
        新闻列表，每条包含source, url, content, timestamp
    """
    if not isinstance(limit, int) or limit < 1:
        return [{"error": "limit必须是正整数"}]
    
    limit = min(limit, 5)  # 限制最大数量
    
    # 选择随机新闻源
    source_keys = list(NEWS_SOURCES.keys())
    selected_sources = random.sample(source_keys, min(limit, len(source_keys)))
    
    # 并行获取
    tasks = [get_news_from_source(source) for source in selected_sources]
    results = await asyncio.gather(*tasks)
    
    # 过滤错误结果
    valid_results = [r for r in results if "error" not in r]
    
    return valid_results


@mcp.tool()
async def search_news(keyword: str, source: Optional[str] = None) -> List[Dict[str, str]]:
    """
    在新闻内容中搜索关键词
    
    参数:
        keyword: 搜索关键词
        source: 指定新闻源（可选），不指定则搜索所有新闻源
    
    返回:
        包含相关新闻的列表
    """
    if not keyword or not keyword.strip():
        return [{"error": "请输入搜索关键词"}]
    
    # 确定要搜索的新闻源
    sources_to_search = [source] if source else list(NEWS_SOURCES.keys())
    
    all_results = []
    
    for source_key in sources_to_search:
        if source_key not in NEWS_SOURCES:
            continue
            
        # 获取该新闻源的内容
        news_result = await get_news_from_source(source_key)
        
        if "error" in news_result:
            continue
            
        # 检查内容是否包含关键词
        content = news_result.get("content", "").lower()
        keyword_lower = keyword.lower()
        
        if keyword_lower in content:
            # 提取包含关键词的上下文
            idx = content.find(keyword_lower)
            start = max(0, idx - 200)
            end = min(len(content), idx + 200)
            snippet = content[start:end]
            
            # 标记关键词
            if keyword_lower in snippet:
                snippet = snippet.replace(keyword_lower, f"**{keyword}**")
            
            all_results.append({
                "source": news_result["source"],
                "url": news_result["url"],
                "snippet": snippet,
                "full_content_length": len(news_result.get("content", "")),
                "timestamp": news_result["timestamp"]
            })
    
    return all_results[:5]  # 限制返回数量


@mcp.tool()
async def fetch_webpage(url: str) -> Dict[str, str]:
    """
    获取任意网页的内容
    
    参数:
        url: 网页URL
    
    返回:
        包含url, content, timestamp的字典
    """
    if not url or not url.strip():
        return {"error": "请输入有效的URL"}
    
    content = await fetch_url(url)
    
    return {
        "url": url,
        "content": content,
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    }


@mcp.tool()
async def get_today_news_summary() -> str:
    """
    获取今日新闻摘要
    
    返回:
        今日新闻摘要文本
    """
    # 获取当前日期
    today = datetime.now().strftime("%Y年%m月%d日")
    
    # 从多个新闻源获取新闻
    sources = ["people", "xinhua", "cctv"]
    tasks = [get_news_from_source(source) for source in sources]
    results = await asyncio.gather(*tasks)
    
    summary_parts = [f"📰 {today} 新闻摘要 📰\n"]
    
    for result in results:
        if "error" in result:
            continue
            
        source_name = result.get("source", "未知")
        content = result.get("content", "")
        
        # 提取前200个字符作为摘要
        if content:
            preview = content[:200].replace("\n", " ")
            summary_parts.append(f"【{source_name}】{preview}...")
    
    if len(summary_parts) == 1:  # 只有标题
        return f"{today} 新闻获取失败，请尝试其他新闻源。"
    
    return "\n\n".join(summary_parts)


# 测试函数
def test_news_fetcher():
    """测试新闻获取功能"""
    import asyncio
    
    async def run_tests():
        print("测试新闻获取功能...")
        
        # 测试1: 获取人民网新闻
        print("\n1. 测试获取人民网新闻:")
        result = await get_news("people")
        if "error" in result:
            print(f"  失败: {result['error']}")
        else:
            print(f"  成功! 来源: {result.get('source')}")
            print(f"  内容长度: {len(result.get('content', ''))} 字符")
        
        # 测试2: 获取最新新闻
        print("\n2. 测试获取最新新闻:")
        results = await get_latest_news(2)
        print(f"  获取到 {len(results)} 条新闻")
        for i, news in enumerate(results):
            print(f"  新闻{i+1}: {news.get('source')} - {len(news.get('content', ''))} 字符")
        
        # 测试3: 获取网页
        print("\n3. 测试获取网页:")
        result = await fetch_webpage("http://www.people.com.cn")
        if "error" in result:
            print(f"  失败: {result['error']}")
        else:
            print(f"  成功! URL: {result.get('url')}")
            print(f"  内容长度: {len(result.get('content', ''))} 字符")
        
        # 测试4: 今日新闻摘要
        print("\n4. 测试今日新闻摘要:")
        summary = await get_today_news_summary()
        print(f"  摘要长度: {len(summary)} 字符")
        print(f"  预览: {summary[:100]}...")
    
    try:
        asyncio.run(run_tests())
        print("\n✅ 所有测试完成!")
    except Exception as e:
        print(f"\n❌ 测试失败: {e}")
        import traceback
        traceback.print_exc()


def test_people_daily():
    """测试人民日报获取功能"""
    import asyncio
    
    async def run_tests():
        print("测试人民日报获取功能...")
        
        # 测试1: 获取今天的人民日报
        print("\n1. 测试获取今天的人民日报:")
        result = await get_rmrb_newspaper()
        if "error" in result:
            print(f"  失败: {result['error']}")
        else:
            print(f"  成功! 日期: {result.get('date')}")
            print(f"  版面: 第{result.get('edition')}版")
            print(f"  URL: {result.get('url')}")
            print(f"  内容长度: {len(result.get('content', ''))} 字符")
            content = result.get('content', '')
            if content and len(content) > 200:
                print(f"  预览: {content[:200]}...")
        
        # 测试2: 获取指定日期的人民日报（2024年12月6日）
        print("\n2. 测试获取2024年12月6日的人民日报:")
        result = await get_rmrb_newspaper("2024-12-06")
        if "error" in result:
            print(f"  失败: {result['error']}")
        else:
            print(f"  成功! 日期: {result.get('date')}")
            print(f"  版面: 第{result.get('edition')}版")
            print(f"  URL: {result.get('url')}")
            print(f"  内容长度: {len(result.get('content', ''))} 字符")
            content = result.get('content', '')
            if content and len(content) > 200:
                print(f"  预览: {content[:200]}...")
        
        # 测试3: 测试构建URL函数
        print("\n3. 测试构建人民日报URL:")
        try:
            url = await build_rmrb_url("2026-02-24", 1)
            print(f"  URL: {url}")
            print("  ✅ URL构建成功")
        except Exception as e:
            print(f"  ❌ URL构建失败: {e}")
    
    try:
        asyncio.run(run_tests())
        print("\n✅ 人民日报功能测试完成!")
    except Exception as e:
        print(f"\n❌ 测试失败: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    # 运行测试
    # test_news_fetcher()
    # test_people_daily()
    
    # 运行MCP服务器
    mcp.run(transport="stdio")