package com.bond.mail.ui.components

import android.webkit.WebView

/** Run before the visual-state fence. No sender code or mail text is evaluated. */
internal fun WebView.applyMailDarkMode(onComplete: () -> Unit) {
    evaluateJavascript(MAIL_DARK_MODE_SCRIPT) { onComplete() }
}

/**
 * Work from Chromium's resolved styles (including classes, bgcolor, inheritance and !important),
 * not regex guesses about inline CSS. Only AUTO night documents opt in. A new light document is
 * loaded on theme changes, so neither the stored message nor the daytime palette is modified.
 */
internal val MAIL_DARK_MODE_SCRIPT = """
    (function() {
      const root = document.getElementById('bondmail-message-body');
      if (!root || !document.body.classList.contains('bondmail-dark-mode')) return false;
      if (root.dataset.bondmailDarkReady) return true;
      const canvas = [18,18,20,1];
      const media = 'img,svg,video,canvas,picture';
      const set = (el, key, value) => el.style.setProperty(key,value,'important');
      const parse = value => {
        const m = value.match(/^rgba?\(([^)]+)\)/);
        if (!m) return null;
        const n = m[1].split(/[\s,\/]+/).map(Number);
        return [n[0],n[1],n[2],n.length > 3 ? n[3] : 1];
      };
      const css = c => 'rgba('+c.map((v,i)=>i<3?Math.round(v):v).join(',')+')';
      const over = (a,b) => a.slice(0,3).map((v,i)=>v*a[3]+b[i]*(1-a[3])).concat(1);
      const luminance = c => {
        const n=c.slice(0,3).map(v=>{v/=255;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4)});
        return n[0]*.2126+n[1]*.7152+n[2]*.0722;
      };
      const contrast = (a,b) => {
        const x=luminance(over(a,b)),y=luminance(b);
        return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
      };
      const darken = c => {
        if (luminance(c)<=.12) return c;
        // White paper becomes near-black; subtly tinted panels retain their separation.
        if(Math.max(...c.slice(0,3))-Math.min(...c.slice(0,3))<24) {
          const value=18+(1-luminance(c))*20;
          return [value,value,value+2,c[3]];
        }
        const max=Math.max(...c.slice(0,3),1);
        return c.slice(0,3).map(v=>18+v/max*14).concat(c[3]);
      };
      const readable = (fg,bg,link) => {
        const branded=fg && Math.max(...fg.slice(0,3))-Math.min(...fg.slice(0,3))>=24;
        let color=link && !branded?[138,180,248,1]:(fg || [232,234,237,1]);
        // Small newsletter copy is often scaled down; give neutral body text extra headroom.
        const neutral=Math.max(...color.slice(0,3))-Math.min(...color.slice(0,3))<24;
        const target=neutral && !link?7:4.5;
        if (contrast(color,bg)>=target) return color;
        // Blend towards the better endpoint; keep as much original hue as contrast allows.
        const white=[245,245,247,1], black=[16,16,18,1];
        const end=contrast(white,bg)>contrast(black,bg)?white:black;
        for(let step=1;step<=20;step++) {
          const t=step/20;
          const candidate=color.slice(0,3).map((v,i)=>v*(1-t)+end[i]*t).concat(1);
          if(contrast(candidate,bg)>=target+.1) return candidate;
        }
        return end;
      };
      const mediaStyles=[...root.querySelectorAll(media)].filter(el=>!el.parentElement.closest('svg,picture')).map(el=>{
        const layers=[];
        for(let p=el;p && p!==root;p=p.parentElement) layers.push(parse(getComputedStyle(p).backgroundColor)||[0,0,0,0]);
        let bg=[245,245,245,1];
        for(const layer of layers.reverse()) bg=over(layer,bg);
        return {el,bg,color:getComputedStyle(el).color};
      });
      // Snapshot before any writes: changing a parent must not erase a child's original palette.
      const entries=[root,...root.querySelectorAll('*')].filter(el=>!el.closest(media) &&
        !['STYLE','LINK','META'].includes(el.tagName)).map(el=>{
          const s=getComputedStyle(el);
          return {el,bg:parse(s.backgroundColor),fg:parse(s.webkitTextFillColor)||parse(s.color),
            image:s.backgroundImage,display:s.display,visibility:s.visibility,opacity:Number(s.opacity),
            clipped:s.maxHeight==='0px' && s.overflowY==='hidden'};
        });
      const state=new Map();
      for(const item of entries) {
        const {el}=item;
        const parent=state.get(el.parentElement)||{bg:canvas,hidden:false,image:false};
        const hidden=parent.hidden || item.display==='none' || item.visibility!=='visible' ||
          item.opacity===0 || item.clipped;
        let bg=el===root?canvas:darken(item.bg||[0,0,0,0]);
        const hasImage=item.image && item.image!=='none';
        // Background artwork stays intact. Text over it gets its own small opaque backing below.
        if(hasImage && item.bg) bg=item.bg;
        const effective=over(bg,parent.bg);
        const onImage=hasImage || (parent.image && bg[3]<1);
        state.set(el,{bg:effective,hidden,image:onImage});
        if(hidden) continue;
        set(el,'background-color',css(bg));
        set(el,'color-scheme','only light'); // Prevent a second WebView/OEM color transformation.
        if(item.opacity>0 && item.opacity<1) set(el,'opacity','1');
        // Sender blend/filter effects can undo an otherwise correct contrast pair.
        set(el,'mix-blend-mode','normal');
        set(el,'filter','none');
        const fg=item.fg;
        // Transparent copy is often a preheader or image-replacement label. Never expose it.
        if(fg && fg[3]===0) {
          set(el,'color','transparent');
          set(el,'-webkit-text-fill-color','transparent');
          continue;
        }
        const textBg=onImage?canvas:effective;
        const color=css(readable(fg,textBg,!!el.closest('a')));
        set(el,'color',color);
        set(el,'-webkit-text-fill-color',color);
        set(el,'text-shadow','none');
        set(el,'-webkit-text-stroke-width','0');
        if(onImage) {
          for(const node of [...el.childNodes]) {
            if(node.nodeType!==Node.TEXT_NODE || !node.textContent.trim()) continue;
            // A private tag avoids sender rules such as "p > span { display:none }".
            const span=document.createElement('bondmail-dark-text');
            span.dataset.bondmailDarkText='';
            set(span,'display','inline');
            set(span,'font','inherit');
            set(span,'background-color',css(canvas));
            set(span,'color',color);
            set(span,'-webkit-text-fill-color',color);
            set(span,'-webkit-box-decoration-break','clone');
            node.replaceWith(span);span.appendChild(node);
          }
        }
      }
      // Preserve each image's original backing: white wordmarks need their dark plate, while
      // dark transparent logos and QR modules need a light one. Keep SVG currentColor too.
      mediaStyles.forEach(({el,bg,color})=>{
        const artwork=el.dataset.bondmailNightArtwork;
        if(artwork) {
          set(el,'background-color','transparent');
          set(el,'filter',artwork==='monochrome'?'invert(1)':'brightness(1.65)');
          set(el,'mix-blend-mode','normal');
          return;
        }
        set(el,'background-color',css(bg));
        set(el,'color',color);
        set(el,'-webkit-text-fill-color',color);
        set(el,'filter','none');
        set(el,'mix-blend-mode','normal');
      });
      root.dataset.bondmailDarkReady='true';
      return true;
    })();
""".trimIndent()
