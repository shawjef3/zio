"use strict";(self.webpackChunkzio_site=self.webpackChunkzio_site||[]).push([[414],{3905:function(e,t,n){n.d(t,{Zo:function(){return c},kt:function(){return m}});var r=n(7294);function a(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function i(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);t&&(r=r.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,r)}return n}function o(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?i(Object(n),!0).forEach((function(t){a(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):i(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function l(e,t){if(null==e)return{};var n,r,a=function(e,t){if(null==e)return{};var n,r,a={},i=Object.keys(e);for(r=0;r<i.length;r++)n=i[r],t.indexOf(n)>=0||(a[n]=e[n]);return a}(e,t);if(Object.getOwnPropertySymbols){var i=Object.getOwnPropertySymbols(e);for(r=0;r<i.length;r++)n=i[r],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(a[n]=e[n])}return a}var s=r.createContext({}),p=function(e){var t=r.useContext(s),n=t;return e&&(n="function"==typeof e?e(t):o(o({},t),e)),n},c=function(e){var t=p(e.components);return r.createElement(s.Provider,{value:t},e.children)},d={inlineCode:"code",wrapper:function(e){var t=e.children;return r.createElement(r.Fragment,{},t)}},u=r.forwardRef((function(e,t){var n=e.components,a=e.mdxType,i=e.originalType,s=e.parentName,c=l(e,["components","mdxType","originalType","parentName"]),u=p(n),m=a,f=u["".concat(s,".").concat(m)]||u[m]||d[m]||i;return n?r.createElement(f,o(o({ref:t},c),{},{components:n})):r.createElement(f,o({ref:t},c))}));function m(e,t){var n=arguments,a=t&&t.mdxType;if("string"==typeof e||a){var i=n.length,o=new Array(i);o[0]=u;var l={};for(var s in t)hasOwnProperty.call(t,s)&&(l[s]=t[s]);l.originalType=e,l.mdxType="string"==typeof e?e:a,o[1]=l;for(var p=2;p<i;p++)o[p]=n[p];return r.createElement.apply(null,o)}return r.createElement.apply(null,n)}u.displayName="MDXCreateElement"},8623:function(e,t,n){n.r(t),n.d(t,{frontMatter:function(){return l},contentTitle:function(){return s},metadata:function(){return p},toc:function(){return c},default:function(){return u}});var r=n(7462),a=n(3366),i=(n(7294),n(3905)),o=["components"],l={id:"with-scalaz-7x",title:"How to Interop with Scalaz 7.x?"},s=void 0,p={unversionedId:"howto/interop/with-scalaz-7x",id:"version-1.x/howto/interop/with-scalaz-7x",isDocsHomePage:!1,title:"How to Interop with Scalaz 7.x?",description:"ZIO Instances",source:"@site/versioned_docs/version-1.x/howto/interop/with-scalaz-7x.md",sourceDirName:"howto/interop",slug:"/howto/interop/with-scalaz-7x",permalink:"/1.x/howto/interop/with-scalaz-7x",editUrl:"https://github.com/zio/zio/edit/series/2.x/versioned_docs/version-1.x/howto/interop/with-scalaz-7x.md",tags:[],version:"1.x",frontMatter:{id:"with-scalaz-7x",title:"How to Interop with Scalaz 7.x?"},sidebar:"howto-sidebar",previous:{title:"How to Interop with Monix?",permalink:"/1.x/howto/interop/with-monix"},next:{title:"How to Interop with Reactive Streams?",permalink:"/1.x/howto/interop/with-reactive-streams"}},c=[{value:"<code>ZIO</code> Instances",id:"zio-instances",children:[{value:"Example",id:"example",children:[],level:3}],level:2},{value:"<code>ZIO</code> parallel <code>Applicative</code> instance",id:"zio-parallel-applicative-instance",children:[{value:"Example",id:"example-1",children:[],level:3}],level:2}],d={toc:c};function u(e){var t=e.components,n=(0,a.Z)(e,o);return(0,i.kt)("wrapper",(0,r.Z)({},d,n,{components:t,mdxType:"MDXLayout"}),(0,i.kt)("h2",{id:"zio-instances"},(0,i.kt)("inlineCode",{parentName:"h2"},"ZIO")," Instances"),(0,i.kt)("p",null,"If you are a happy Scalaz 7.2 user ",(0,i.kt)("a",{parentName:"p",href:"https://github.com/zio/interop-scalaz"},(0,i.kt)("inlineCode",{parentName:"a"},"interop-scala7x"))," module offers ",(0,i.kt)("inlineCode",{parentName:"p"},"ZIO")," instances for several typeclasses."),(0,i.kt)("h3",{id:"example"},"Example"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},"import scalaz._, Scalaz._\nimport zio.interop.scalaz72._\n\ntype Database = IList[User]\n\ndef findUser(id: UserId): ZIO[Database, UserError, User] = ...\ndef findUsers(ids: IList[UserId]): ZIO[Database, UserError, IList[User]] = ids.traverse(findUser(_))\n")),(0,i.kt)("h2",{id:"zio-parallel-applicative-instance"},(0,i.kt)("inlineCode",{parentName:"h2"},"ZIO")," parallel ",(0,i.kt)("inlineCode",{parentName:"h2"},"Applicative")," instance"),(0,i.kt)("p",null,"Due to ",(0,i.kt)("inlineCode",{parentName:"p"},"Applicative")," and ",(0,i.kt)("inlineCode",{parentName:"p"},"Monad")," coherence law ",(0,i.kt)("inlineCode",{parentName:"p"},"ZIO"),"'s ",(0,i.kt)("inlineCode",{parentName:"p"},"Applicative")," instance has to be implemented in terms of ",(0,i.kt)("inlineCode",{parentName:"p"},"bind")," hence when composing multiple effects using ",(0,i.kt)("inlineCode",{parentName:"p"},"Applicative")," they will be sequenced. To cope with that limitation ",(0,i.kt)("inlineCode",{parentName:"p"},"ZIO")," tagged with ",(0,i.kt)("inlineCode",{parentName:"p"},"Parallel")," has an ",(0,i.kt)("inlineCode",{parentName:"p"},"Applicative")," instance which is not ",(0,i.kt)("inlineCode",{parentName:"p"},"Monad")," and operates in parallel."),(0,i.kt)("h3",{id:"example-1"},"Example"),(0,i.kt)("pre",null,(0,i.kt)("code",{parentName:"pre",className:"language-scala"},"import scalaz._, Scalaz._\nimport zio.interop.scalaz72._\n\ncase class Dashboard(details: UserDetails, history: TransactionHistory)\n\ndef getDetails(id: UserId): ZIO[Database, UserError, UserDetails] = ...\ndef getHistory(id: UserId): ZIO[Database, UserError, TransactionHistory] = ...\n\ndef buildDashboard(id: UserId): ZIO[Database, UserError, Dashboard] =\n  Tag.unwrap(^(par(getDetails(id)), par(getHistory(id)))(Dashboard.apply))\n\ndef par[R, E, A](io: ZIO[R, E, A]): scalaz72.ParIO[R, E, A] = Tag(io)\n")))}u.isMDXComponent=!0}}]);