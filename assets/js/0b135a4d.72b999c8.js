"use strict";(self.webpackChunkzio_site=self.webpackChunkzio_site||[]).push([[8312],{3905:function(e,n,t){t.d(n,{Zo:function(){return u},kt:function(){return d}});var r=t(7294);function o(e,n,t){return n in e?Object.defineProperty(e,n,{value:t,enumerable:!0,configurable:!0,writable:!0}):e[n]=t,e}function a(e,n){var t=Object.keys(e);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);n&&(r=r.filter((function(n){return Object.getOwnPropertyDescriptor(e,n).enumerable}))),t.push.apply(t,r)}return t}function i(e){for(var n=1;n<arguments.length;n++){var t=null!=arguments[n]?arguments[n]:{};n%2?a(Object(t),!0).forEach((function(n){o(e,n,t[n])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(t)):a(Object(t)).forEach((function(n){Object.defineProperty(e,n,Object.getOwnPropertyDescriptor(t,n))}))}return e}function c(e,n){if(null==e)return{};var t,r,o=function(e,n){if(null==e)return{};var t,r,o={},a=Object.keys(e);for(r=0;r<a.length;r++)t=a[r],n.indexOf(t)>=0||(o[t]=e[t]);return o}(e,n);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(r=0;r<a.length;r++)t=a[r],n.indexOf(t)>=0||Object.prototype.propertyIsEnumerable.call(e,t)&&(o[t]=e[t])}return o}var p=r.createContext({}),l=function(e){var n=r.useContext(p),t=n;return e&&(t="function"==typeof e?e(n):i(i({},n),e)),t},u=function(e){var n=l(e.components);return r.createElement(p.Provider,{value:n},e.children)},s={inlineCode:"code",wrapper:function(e){var n=e.children;return r.createElement(r.Fragment,{},n)}},f=r.forwardRef((function(e,n){var t=e.components,o=e.mdxType,a=e.originalType,p=e.parentName,u=c(e,["components","mdxType","originalType","parentName"]),f=l(t),d=o,y=f["".concat(p,".").concat(d)]||f[d]||s[d]||a;return t?r.createElement(y,i(i({ref:n},u),{},{components:t})):r.createElement(y,i({ref:n},u))}));function d(e,n){var t=arguments,o=n&&n.mdxType;if("string"==typeof e||o){var a=t.length,i=new Array(a);i[0]=f;var c={};for(var p in n)hasOwnProperty.call(n,p)&&(c[p]=n[p]);c.originalType=e,c.mdxType="string"==typeof e?e:o,i[1]=c;for(var l=2;l<a;l++)i[l]=t[l];return r.createElement.apply(null,i)}return r.createElement.apply(null,t)}f.displayName="MDXCreateElement"},156:function(e,n,t){t.r(n),t.d(n,{frontMatter:function(){return c},contentTitle:function(){return p},metadata:function(){return l},toc:function(){return u},default:function(){return f}});var r=t(7462),o=t(3366),a=(t(7294),t(3905)),i=["components"],c={id:"zrefsynchronized",title:"ZRef.Synchronized"},p=void 0,l={unversionedId:"datatypes/concurrency/zrefsynchronized",id:"datatypes/concurrency/zrefsynchronized",isDocsHomePage:!1,title:"ZRef.Synchronized",description:"A ZRef.Synchronized[RA, RB, EA, EB, A, B] is a polymorphic, purely functional description of a mutable reference.",source:"@site/docs/datatypes/concurrency/zrefsynchronized.md",sourceDirName:"datatypes/concurrency",slug:"/datatypes/concurrency/zrefsynchronized",permalink:"/datatypes/concurrency/zrefsynchronized",editUrl:"https://github.com/zio/zio/edit/series/2.x/docs/datatypes/concurrency/zrefsynchronized.md",tags:[],version:"current",frontMatter:{id:"zrefsynchronized",title:"ZRef.Synchronized"},sidebar:"datatypes-sidebar",previous:{title:"ZRef",permalink:"/datatypes/concurrency/zref"},next:{title:"Ref",permalink:"/datatypes/concurrency/ref"}},u=[],s={toc:u};function f(e){var n=e.components,t=(0,o.Z)(e,i);return(0,a.kt)("wrapper",(0,r.Z)({},s,t,{components:n,mdxType:"MDXLayout"}),(0,a.kt)("p",null,"A ",(0,a.kt)("inlineCode",{parentName:"p"},"ZRef.Synchronized[RA, RB, EA, EB, A, B]")," is a polymorphic, purely functional description of a mutable reference. "),(0,a.kt)("p",null,"The fundamental operations of a ",(0,a.kt)("inlineCode",{parentName:"p"},"ZRef.Synchronized"),"are ",(0,a.kt)("inlineCode",{parentName:"p"},"set")," and ",(0,a.kt)("inlineCode",{parentName:"p"},"get"),". "),(0,a.kt)("ul",null,(0,a.kt)("li",{parentName:"ul"},(0,a.kt)("strong",{parentName:"li"},(0,a.kt)("inlineCode",{parentName:"strong"},"set"))," takes a value of type ",(0,a.kt)("inlineCode",{parentName:"li"},"A")," and sets the reference to a new value, requiring an environment of type ",(0,a.kt)("inlineCode",{parentName:"li"},"RA")," and potentially failing with an error of type ",(0,a.kt)("inlineCode",{parentName:"li"},"EA"),". "),(0,a.kt)("li",{parentName:"ul"},(0,a.kt)("strong",{parentName:"li"},(0,a.kt)("inlineCode",{parentName:"strong"},"get"))," gets the current value of the reference and returns a value of type ",(0,a.kt)("inlineCode",{parentName:"li"},"B"),", requiring an environment of type\n",(0,a.kt)("inlineCode",{parentName:"li"},"RB")," and potentially failing with an error of type ",(0,a.kt)("inlineCode",{parentName:"li"},"EB"),".")),(0,a.kt)("p",null,"When the error and value types of the ",(0,a.kt)("inlineCode",{parentName:"p"},"ZRef.Synchronized")," are unified, that is, it is a ",(0,a.kt)("inlineCode",{parentName:"p"},"ZRef.Synchronized[E, E, A, A]"),", the ",(0,a.kt)("inlineCode",{parentName:"p"},"ZRef.Synchronized")," also supports atomic ",(0,a.kt)("inlineCode",{parentName:"p"},"modify")," and ",(0,a.kt)("inlineCode",{parentName:"p"},"update")," operations."),(0,a.kt)("blockquote",null,(0,a.kt)("p",{parentName:"blockquote"},(0,a.kt)("em",{parentName:"p"},(0,a.kt)("strong",{parentName:"em"},"Note:"))),(0,a.kt)("p",{parentName:"blockquote"},"Unlike ",(0,a.kt)("inlineCode",{parentName:"p"},"ZRef"),", ",(0,a.kt)("inlineCode",{parentName:"p"},"ZRef.Synchronized")," allows performing effects within update operations, at some cost to performance. Writes will semantically block other writers, while multiple readers can read simultaneously.")))}f.isMDXComponent=!0}}]);