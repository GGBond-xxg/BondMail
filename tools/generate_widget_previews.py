"""Generate public widget-picker artwork; never read an account or mailbox.
Requires Pillow. Run from the repository root on Windows (Microsoft YaHei fonts).
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1] / "app/src/main/res"
SCALE = 3
INK, SECONDARY, ACCENT = "#242039", "#817790", "#7658A6"

def render(size, labels, target):
    w,h = size
    image = Image.new("RGBA", (w*SCALE,h*SCALE))
    d = ImageDraw.Draw(image)
    def box(rect, fill, radius=0, outline=None):
        d.rounded_rectangle(tuple(round(v*SCALE) for v in rect), radius*SCALE, fill, outline, width=SCALE)
    def line(points, fill, width=2):
        d.line([(int(x*SCALE),int(y*SCALE)) for x,y in points], fill=fill, width=width*SCALE, joint="curve")
    def text(x,y,value,size=12,color=INK,bold=False):
        font = ImageFont.truetype("C:/Windows/Fonts/"+("msyhbd.ttc" if bold else "msyh.ttc"),size*SCALE)
        d.text((x*SCALE,y*SCALE),value,font=font,fill=color)
    def envelope(x,y,size):
        box((x,y,x+size,y+size),"#E6D8F6",8)
        box((x+size*.16,y+size*.24,x+size*.84,y+size*.76),None,2,ACCENT)
        line([(x+size*.18,y+size*.27),(x+size*.5,y+size*.53),(x+size*.82,y+size*.27)],ACCENT)
    def action(x,y,plus=False):
        box((x-16,y-16,x+16,y+16),"#DECDF2",16)
        if plus:
            line([(x-6,y),(x+6,y)],ACCENT);line([(x,y-6),(x,y+6)],ACCENT)
        else:
            line([(x-6,y+5),(x-4,y),(x+4,y-8),(x+8,y-4),(x,y+4),(x-6,y+5)],ACCENT)
    box((1,1,w-1,h-1),"#F6F0FC",23,"#E9DEF4")
    if w < 200:
        envelope(18,19,34)
        text(18,66,labels[0],13,bold=True)
        text(18,88,"12",28,bold=True)
        text(18,129,labels[1],10,SECONDARY)
        action(w-34,h-33,True)
    else:
        envelope(15,16,30)
        text(54,15,labels[0],13,bold=True)
        text(54,36,labels[2],9,SECONDARY)
        action(w-31,31)
        count = 3 if h < 250 else 6
        rowh = (h-75)/count
        for i in range(count):
            y = 66+i*rowh
            box((16,y+8,42,y+34),["#E1D5F3","#D5EADD","#F1D6E3"][i%3],13)
            text(23,y+10,chr(65+i),11,ACCENT)
            text(53,y+4,labels[3],11,bold=True)
            text(w-49,y+7,"09:41",8,SECONDARY)
            # Neutral text bars describe the layout without fabricating private mail content.
            box((53,y+27,w-61,y+31),"#C7BFD3",2)
    target.parent.mkdir(parents=True,exist_ok=True)
    image.save(target,optimize=True)

for qualifier,labels in [
    ("drawable-nodpi",("收件箱","未读邮件","示例预览","发件人")),
    ("drawable-en-nodpi",("Inbox","unread","Preview","Sender")),
    ("drawable-zh-rTW-nodpi",("收件匣","未讀郵件","範例預覽","寄件者")),
]:
    for name,size in [("small",(160,170)),("medium",(320,190)),("large",(320,350))]:
        render(size,labels,ROOT/qualifier/f"widget_preview_{name}.png")
