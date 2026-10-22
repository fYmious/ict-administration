import asyncio, json, sys
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client


async def main(url, tool=None, args=None):
    async with streamablehttp_client(url) as (r, w, _):
        async with ClientSession(r, w) as s:
            await s.initialize()
            if tool is None:
                for t in (await s.list_tools()).tools:
                    print(f"- {t.name}: {t.description.splitlines()[0]}")
            else:
                res = await s.call_tool(tool, args or {})
                for c in res.content:
                    print(c.text)

url = sys.argv[1]
tool = sys.argv[2] if len(sys.argv) > 2 else None
args = json.loads(sys.argv[3]) if len(sys.argv) > 3 else None
asyncio.run(main(url, tool, args))
