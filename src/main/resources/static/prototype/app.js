const main = document.querySelector('#main');
const dialog = document.querySelector('#dialog');
const switcher = document.querySelector('#switcher');
const notification = document.querySelector('#notification');
const variants = { A: '图文首页', B: '筛选工作台', C: '食记首页' };
const mealTypes = { BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐', SNACK: '加餐' };
const ratingFields = { taste: '味道', valueScore: '性价比', portion: '分量', recommendation: '推荐程度' };
const photos = new Map([
    ['番茄牛腩饭', 'tomato-beef.jpg'],
    ['香菇鸡肉面', 'chicken-noodles.jpg'],
    ['麻辣香锅', 'mala-hotpot.jpg'],
]);
const icons = {
    bowl: '<path d="M3 13h18a9 9 0 0 1-18 0Zm5 8h8M8 10c-2-2 2-3 0-5m4 5c-2-2 2-3 0-5m4 5c-2-2 2-3 0-5"/>',
    home: '<path d="m3 10 9-7 9 7M5 9v10a1 1 0 0 0 1 1h4v-6h4v6h4a1 1 0 0 0 1-1V9"/>',
    recipe: '<path d="M5 5a2 2 0 0 1 2-2h13v17H7a2 2 0 0 1-2-2V5Zm0 13a2 2 0 0 1 2-2h13M9 7h7M9 11h5"/>',
    market: '<path d="M4 9h16l-2 10a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1L4 9Zm4 0 4-5 4 5M9 13l1 4m5-4-1 4"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    left: '<path d="m14 6-6 6 6 6"/>',
    right: '<path d="m10 6 6 6-6 6"/>',
    arrow: '<path d="M4 12h15m-6-6 6 6-6 6"/>',
    dice: '<rect x="3" y="3" width="18" height="18" rx="4"/><path d="M8 8h.01M16 8h.01M12 12h.01M8 16h.01M16 16h.01" stroke-width="3"/>',
    search: '<circle cx="10.5" cy="10.5" r="5.5"/><path d="m15 15 5 5"/>',
    heart: '<path d="M20.8 8.9c0 4.1-8.8 9.1-8.8 9.1S3.2 13 3.2 8.9a4.5 4.5 0 0 1 8.8-1.5 4.5 4.5 0 0 1 8.8 1.5Z"/>',
    star: '<path d="m12 3 2.7 5.5 6.1.9-4.4 4.3 1.1 6.1-5.5-2.9-5.5 2.9 1.1-6.1-4.4-4.3 6.1-.9Z"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>',
};

const requestedVariant = new URLSearchParams(location.search).get('variant');
const loopback = ['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname);
const localPreview = loopback || location.protocol === 'file:';
const emptyFilters = { keyword: '', canteenId: '', maxPrice: '', category: '', mark: '' };
const state = {
    variant: localPreview && Object.hasOwn(variants, requestedVariant) ? requestedVariant : 'A',
    canteens: [], stalls: [], dishes: [], meals: [], drafts: [],
    ...emptyFilters,
    month: campusDate().slice(0, 7),
};
let notificationTimer;
let dialogTrigger;

function icon(name) {
    return `<span class="icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${icons[name]}</svg></span>`;
}

function escape(value) {
    const entities = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
    return String(value ?? '').replace(/[&<>"']/g, character => entities[character]);
}

function campusDate(daysAgo = 0) {
    const date = new Date(Date.now() - daysAgo * 86_400_000);
    return new Intl.DateTimeFormat('sv-SE', {
        timeZone: 'Asia/Shanghai', dateStyle: 'short', timeStyle: 'short',
    }).format(date).replace(' ', 'T');
}

function money(value) {
    return `¥${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
}

function demoData() {
    const canteens = [{ id: 1, name: '第一食堂', location: '校园东侧' }];
    const stalls = [{ id: 1, canteenId: 1, name: '家常菜档口' }];
    const dishes = [
        { id: 1, name: '番茄牛腩饭', price: 15, category: '米饭', flavors: ['酸甜', '不辣'], favorite: true },
        { id: 2, name: '香菇鸡肉面', price: 12, category: '面食', flavors: ['清淡', '不辣'] },
        { id: 3, name: '麻辣香锅', price: 20, category: '香锅', flavors: ['麻辣'] },
    ].map(dish => ({ canteenId: 1, stallId: 1, available: true, favorite: false, avoided: false, ...dish }));
    const meals = [
        { dish: dishes[0], mealType: 'LUNCH', time: '12:00', note: '牛肉很软，下次还来。', rating: [5, 4, 5, 5] },
        { dish: dishes[1], mealType: 'DINNER', time: '18:00', note: '汤很鲜。', rating: [4, 5, 4, 4] },
    ].map(({ dish, mealType, time, note, rating }, index) => ({
        eatenAt: `${campusDate(index + 1).slice(0, 10)}T${time}`,
        mealType, note, totalAmount: dish.price,
        items: [{ dishId: dish.id, dishName: dish.name, quantity: 1, paidAmount: dish.price,
            rating: Object.fromEntries(Object.keys(ratingFields).map((key, index) => [key, rating[index]])) }],
    }));
    return { canteens, stalls, dishes, meals };
}

async function get(path) {
    const response = await fetch(path, { signal: AbortSignal.timeout(3000) });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return response.json();
}

async function getPages(path) {
    const size = 100;
    const first = await get(`${path}?size=${size}`);
    const items = [...first.items];
    for (let page = 2; (page - 1) * size < first.total; page++) {
        items.push(...(await get(`${path}?size=${size}&page=${page}`)).items);
    }
    return items;
}

async function load() {
    let sample = true;
    if (location.protocol !== 'file:') {
        try {
            const [canteens, stalls, dishes, meals] = await Promise.all([
                get('/api/canteens?includeArchived=true'), get('/api/stalls?includeArchived=true'),
                getPages('/api/dishes'), getPages('/api/meals'),
            ]);
            Object.assign(state, { canteens, stalls, dishes, meals });
            sample = false;
        } catch (error) {
            console.info('校园食记 API 读取失败，使用原型示例。', error.message);
        }
    }
    if (sample) Object.assign(state, demoData());
    // Put the seed data in the same order in the offline and API previews.
    state.dishes.sort((a, b) => a.id - b.id);
    document.querySelector('#source').textContent = sample ? '数据来源 · 离线示例' : '数据来源 · 本地食记';
    main.removeAttribute('aria-busy');
    render();
}

function view() {
    const hash = location.hash.slice(1);
    if (hash === 'categories') return 'catalog';
    return ['catalog', 'diary'].includes(hash) ? hash : 'home';
}

function dayLabel(date) {
    const [month, day] = date.slice(5, 10).split('-').map(Number);
    return `${month}月${day}日`;
}

function place(dish) {
    const canteen = state.canteens.find(entry => entry.id === dish.canteenId);
    const stall = state.stalls.find(entry => entry.id === dish.stallId);
    return [canteen?.name, stall?.name].filter(Boolean).join(' · ');
}

function cover(dish) {
    if (/^\/api\/photos\/\d+\/content$/.test(dish?.photoUrl)) return { url: dish.photoUrl, sample: false };
    return { url: photos.get(dish?.name), sample: true };
}

function photo(dish, { decorate = false } = {}) {
    const { url, sample } = cover(dish);
    if (!url) return `<div class="photo photo--empty" aria-label="暂无照片">${icon('bowl')}</div>`;
    return `<div class="photo">
        <img src="${escape(url)}" alt="${decorate ? '' : escape(sample ? `${dish.name}示意图` : dish.name)}" loading="lazy">
        ${sample ? '<span class="sample-caption" aria-hidden="true">示意图</span>' : ''}
    </div>`;
}

function allMeals() {
    return [...state.drafts, ...state.meals].sort((a, b) => b.eatenAt.localeCompare(a.eatenAt));
}

function rankedDishes() {
    const scores = new Map();
    for (const meal of allMeals()) {
        for (const { dishId, dishName, rating } of meal.items) {
            if (!rating) continue;
            const entry = scores.get(dishId) ?? { dishId, dishName, total: 0, count: 0 };
            entry.total += Object.keys(ratingFields).reduce((sum, key) => sum + rating[key], 0) / 4;
            entry.count++;
            scores.set(dishId, entry);
        }
    }
    return [...scores.values()].map(entry => ({
        ...entry,
        dishName: state.dishes.find(dish => dish.id === entry.dishId)?.name ?? entry.dishName,
        score: entry.total / entry.count,
    })).sort((a, b) => b.score - a.score || b.count - a.count || a.dishId - b.dishId);
}

function filteredDishes() {
    const keyword = state.keyword.trim().toLocaleLowerCase();
    const category = view() === 'catalog' || state.variant === 'B' ? state.category : '';
    return state.dishes.filter(dish =>
        (!keyword || `${dish.name} ${place(dish)}`.toLocaleLowerCase().includes(keyword)) &&
        (!state.canteenId || dish.canteenId === Number(state.canteenId)) &&
        (!state.maxPrice || Number(dish.price) <= Number(state.maxPrice)) &&
        (!category || dish.category === category) &&
        (!state.mark || dish[state.mark]));
}

function option(value, label, selected) {
    return `<option value="${escape(value)}" ${String(value) === String(selected) ? 'selected' : ''}>${escape(label)}</option>`;
}

function filters() {
    return `<div class="filters">
        <label class="search">${icon('search')}<input type="search" aria-label="搜索菜名、档口或食堂" maxlength="80" data-filter="keyword" placeholder="搜菜名、档口或食堂" value="${escape(state.keyword)}"></label>
        <select data-filter="canteenId" aria-label="按食堂筛选">${option('', '全部食堂', state.canteenId)}${state.canteens.filter(canteen => canteen.active !== false).map(canteen => option(canteen.id, canteen.name, state.canteenId)).join('')}</select>
        <select data-filter="maxPrice" aria-label="按预算筛选">${option('', '不限预算', state.maxPrice)}${[10, 15, 20, 30].map(price => option(price, `${price} 元以内`, state.maxPrice)).join('')}</select>
        <select data-filter="mark" aria-label="按收藏或踩雷筛选">${option('', '全部标记', state.mark)}${option('favorite', '已收藏', state.mark)}${option('avoided', '已踩雷', state.mark)}</select>
    </div>`;
}

function categoryFilter() {
    const counts = new Map();
    for (const { category } of state.dishes) {
        if (category) counts.set(category, (counts.get(category) ?? 0) + 1);
    }
    return `<fieldset class="category-filter"><legend>分类</legend>
        <div class="chip-row">${[['', state.dishes.length], ...counts].map(([category, count]) =>
            `<button class="chip" type="button" data-category="${escape(category)}" aria-pressed="${category === state.category}">${escape(category || '全部')} <span class="chip-count">${count}</span></button>`).join('')}</div>
    </fieldset>`;
}

function score(value) {
    return value == null ? '<span class="muted">待评分</span>' : `<span class="score" aria-label="评分 ${Number(value).toFixed(2)}">${icon('star')}${Number(value).toFixed(2)}</span>`;
}

function card(dish, overall) {
    return `<article class="dish">
        ${photo(dish, { decorate: true })}
        <div class="dish-body">
            <div class="dish-title"><h3>${escape(dish.name)}</h3><span class="price">${money(dish.price)}</span></div>
            <p>${escape(place(dish))}</p>
            <div class="tags">${dish.flavors.map(flavor => `<span class="tag">${escape(flavor)}</span>`).join('')}${dish.avoided ? '<span class="tag">已踩雷</span>' : ''}</div>
            <div class="dish-actions">
                <span class="dish-action-label">${score(overall.get(dish.id) ?? dish.score)}</span>
                <div class="dish-action-label">
                    <button class="text-button" type="button" data-action="detail" data-id="${dish.id}" aria-label="查看 ${escape(dish.name)}">查看 ${icon('arrow')}</button>
                    <button class="icon-button" type="button" data-action="favorite" data-id="${dish.id}" aria-pressed="${dish.favorite}" aria-label="收藏 ${escape(dish.name)}">${icon('heart')}</button>
                </div>
            </div>
        </div>
    </article>`;
}

function empty(title, detail) {
    return `<div class="empty"><strong>${title}</strong><p>${detail}</p></div>`;
}

function results(layout) {
    return `<p class="filter-note" id="match-count" role="status"></p><div id="results" class="dishes dishes--${layout}"></div>`;
}

function renderResults() {
    const destination = document.querySelector('#results');
    if (!destination) return;
    const dishes = filteredDishes();
    const overall = new Map(rankedDishes().map(entry => [entry.dishId, entry.score]));
    document.querySelector('#match-count').textContent = `${dishes.length} 道菜`;
    destination.innerHTML = dishes.length ? dishes.map(dish => card(dish, overall)).join('') :
        empty('没有找到符合条件的菜', '试试更换关键词或放宽筛选条件。');
}

function randomButton(label = '帮我选一道', accent = false) {
    return `<button class="button ${accent ? 'button--accent' : ''}" type="button" data-action="random">${icon('dice')}${label}</button>`;
}

function variantA() {
    return `<div class="home-layout">
        <div class="home-intro">
            <h1 id="page-heading">今天吃什么？</h1>
            ${randomButton('帮我选一道', true)}
        </div>
        <section aria-labelledby="menu-title">
            <h2 class="menu-heading" id="menu-title">食堂菜谱</h2>
            ${filters()}${results('menu')}
        </section>
    </div>`;
}

function variantB(catalog = false) {
    return `<div class="page-heading"><h1 id="page-heading">${catalog ? '菜谱' : '按条件找饭'}</h1></div>
    <div class="finder">
        <aside class="finder-sidebar" aria-label="筛选菜品">${categoryFilter()}${filters()}<button class="button" type="button" data-action="reset">清除筛选</button></aside>
        <section aria-label="菜品结果">
            <div class="finder-banner"><div><strong>选择困难？</strong><p>从符合当前条件的菜里抽一道。</p></div>${randomButton('随机选', true)}</div>
            ${results('list')}
        </section>
    </div>`;
}

function variantC(home = false) {
    const favorites = state.dishes.filter(dish => dish.favorite).length;
    return `<div class="diary-heading">
        <div class="page-heading"><h1 id="page-heading">${home ? '校园饮食日记' : '我的市集'}</h1></div>
        <div class="day-control"><label class="field">统计月份<input type="month" id="month" value="${escape(state.month)}"></label>
            ${home ? `<a class="button" href="#catalog">去找饭 ${icon('arrow')}</a>` : `<button class="button" type="button" data-action="browse-favorites">${icon('heart')} 收藏 ${favorites}</button>`}
        </div>
    </div>
    <div class="diary-contents" id="diary-contents"></div>`;
}

function renderDiary() {
    const destination = document.querySelector('#diary-contents');
    if (!destination) return;
    const meals = allMeals().filter(meal => meal.eatenAt.startsWith(state.month));
    const daily = new Map();
    for (const meal of meals) {
        const date = meal.eatenAt.slice(0, 10);
        daily.set(date, (daily.get(date) ?? 0) + Math.round(Number(meal.totalAmount) * 100));
    }
    const cents = [...daily.values()].reduce((sum, amount) => sum + amount, 0);
    const ranks = rankedDishes().slice(0, 5);
    destination.innerHTML = `<div class="metrics" aria-label="本月统计">
        <div class="metric"><span>本月餐费</span><strong>${money(cents / 100)}</strong></div>
        <div class="metric"><span>用餐次数</span><strong>${meals.length}</strong></div>
        <div class="metric"><span>平均每餐</span><strong>${money(meals.length ? Math.round(cents / meals.length) / 100 : 0)}</strong></div>
    </div>
    <div class="journal-layout">
        <section class="section" aria-labelledby="meals-title"><h2 id="meals-title">用餐记录</h2>
            ${meals.length ? `<ul class="meals">${meals.map(meal => `<li class="meal">
                <time datetime="${escape(meal.eatenAt)}">${dayLabel(meal.eatenAt)}<small>${mealTypes[meal.mealType] ?? ''}</small></time>
                <div><h3>${meal.items.map(item => escape(item.dishName)).join('、')}</h3>
                    ${meal.note ? `<p>${escape(meal.note)}</p>` : ''}${meal.draft ? '<p>原型草稿</p>' : ''}</div>
                <span class="price">${money(meal.totalAmount)}</span>
            </li>`).join('')}</ul>` : empty('这个月还没有食记', '可以用「记一餐」试记一笔。')}
        </section>
        <section class="section" aria-labelledby="rank-title"><h2 id="rank-title">个人口味榜</h2>
            ${ranks.length ? `<ol class="ranks">${ranks.map((rank, index) => `<li class="rank"><span class="rank-index">${index + 1}</span><div><strong>${escape(rank.dishName)}</strong><p class="muted">${rank.count} 次评价</p></div>${score(rank.score)}</li>`).join('')}</ol>` : empty('还没有评分', '试记一餐并填写评分，就能看到榜单。')}
        </section>
    </div>
    ${daily.size ? `<details class="expense section"><summary>查看逐日消费</summary><ul>${[...daily].map(([date, amount]) => `<li><time datetime="${date}">${dayLabel(date)}</time><span class="price">${money(amount / 100)}</span></li>`).join('')}</ul></details>` : ''}`;
}

function render() {
    const currentView = view();
    document.querySelectorAll('#navigation [data-view]').forEach(link => {
        if (link.dataset.view === currentView) link.setAttribute('aria-current', 'page');
        else link.removeAttribute('aria-current');
    });
    document.querySelector('#variant-label').textContent = `${state.variant} · ${variants[state.variant]}`;
    if (main.hasAttribute('aria-busy')) return;
    if (currentView === 'catalog') main.innerHTML = variantB(true);
    else if (currentView === 'diary') main.innerHTML = variantC();
    else main.innerHTML = { A: variantA, B: variantB, C: () => variantC(true) }[state.variant]();
    renderResults();
    renderDiary();
}

function announce(message) {
    clearTimeout(notificationTimer);
    notification.textContent = message;
    notificationTimer = setTimeout(() => { notification.textContent = ''; }, 3300);
}

function openDialog(title, content, trigger) {
    dialog.innerHTML = `<div class="dialog-header"><h2 id="dialog-title">${escape(title)}</h2>
        <button class="icon-button" type="button" data-action="close" aria-label="关闭" autofocus>${icon('close')}</button></div>
        <div class="dialog-content">${content}</div>`;
    if (!dialog.open) {
        dialogTrigger = trigger;
        dialog.showModal();
    } else {
        dialog.querySelector('[autofocus]').focus();
    }
}

function showDish(dish, trigger, candidateCount) {
    const recommendation = candidateCount != null;
    const overall = rankedDishes().find(entry => entry.dishId === dish.id)?.score ?? dish.score;
    openDialog(recommendation ? '这次就吃这一道？' : dish.name, `
        ${photo(dish, { decorate: !recommendation })}
        ${recommendation ? `<div class="dish-title"><h3>${escape(dish.name)}</h3><span class="price">${money(dish.price)}</span></div>` : `<p class="price">${money(dish.price)}</p>`}
        <p class="muted">${escape([place(dish), dish.category, ...dish.flavors].filter(Boolean).join(' · '))}</p>
        <p>${score(overall)}</p>
        ${recommendation ? `<p class="muted">从 ${candidateCount} 道符合条件且未踩雷的菜中抽出。</p>` : ''}
        <div class="dialog-actions">
            <button class="button button--accent" type="button" data-action="record" data-id="${dish.id}">${icon('plus')} 记一餐</button>
            ${recommendation ? randomButton('再选一次') : `<button class="button" type="button" data-action="avoided" data-id="${dish.id}">${dish.avoided ? '取消踩雷' : '标记踩雷'}</button>`}
        </div>`, trigger);
}

function record(dish, trigger) {
    const choices = state.dishes.filter(entry => entry.available);
    if (!choices.length) {
        openDialog('记一餐', empty('暂无可记录的菜品', '菜谱有可用菜品后，就能在这里试记。'), trigger);
        return;
    }
    const selected = dish ?? choices[0];
    const now = campusDate();
    openDialog('记一餐', `<form class="record-form" id="meal-form">
        <p class="muted">原型草稿仅保留在当前页面，刷新后清除。</p>
        <label class="field">吃了什么<select name="dishId" required>${choices.map(entry => option(entry.id, `${entry.name} · ${place(entry)}`, selected.id)).join('')}</select></label>
        <div class="fields">
            <label class="field">用餐时间<input type="datetime-local" name="eatenAt" value="${now}" max="${now}" required></label>
            <label class="field">餐次<select name="mealType">${Object.entries(mealTypes).map(([key, value]) => option(key, value, 'LUNCH')).join('')}</select></label>
            <label class="field">份数<input type="number" name="quantity" value="1" min="1" max="100" required></label>
            <label class="field">实付总额（元）<input type="number" name="paidAmount" value="${Number(selected.price)}" min="0" max="999999.99" step="0.01" required></label>
        </div>
        <fieldset><legend>评分（可选）</legend><div class="fields">
            ${Object.entries(ratingFields).map(([key, label]) => `<label class="field">${label}<select name="${key}">${option('', '未评分', '')}${[5, 4, 3, 2, 1].map(value => option(value, `${value} 分`)).join('')}</select></label>`).join('')}
        </div></fieldset>
        <label class="field">用餐备注<textarea name="note" rows="2" maxlength="1000" placeholder="这顿吃得怎么样？"></textarea></label>
        <p class="form-error" id="form-error" role="alert"></p>
        <div class="dialog-actions"><button class="button button--accent" type="submit">存为草稿</button><button class="button" type="button" data-action="close">取消</button></div>
    </form>`, trigger);
}

function selectVariant(direction) {
    const keys = Object.keys(variants);
    state.variant = keys[(keys.indexOf(state.variant) + direction + keys.length) % keys.length];
    const url = new URL(location.href);
    if (location.protocol !== 'file:') url.searchParams.set('variant', state.variant);
    url.hash = '';
    history.replaceState(null, '', url);
    render();
    window.scrollTo(0, 0);
}

function setMark(dish, mark, trigger) {
    dish[mark] = !dish[mark];
    if (dish[mark]) dish[mark === 'favorite' ? 'avoided' : 'favorite'] = false;
    const detailOpen = dialog.open;
    renderResults();
    if (detailOpen) {
        dialog.close();
    } else {
        const updated = main.querySelector(`[data-action="${mark}"][data-id="${dish.id}"]`);
        (updated ?? (main.contains(trigger) ? trigger : main.querySelector('[data-filter="mark"]')))?.focus();
    }
    const label = mark === 'favorite' ? '收藏' : '踩雷';
    announce(`${dish[mark] ? '已' : '已取消'}${label} · 原型预览`);
}

document.querySelectorAll('[data-icon]').forEach(element => { element.innerHTML = icon(element.dataset.icon); });
switcher.hidden = !localPreview;
window.addEventListener('hashchange', () => {
    render();
    if (!main.hasAttribute('aria-busy')) main.focus({ preventScroll: true });
    window.scrollTo(0, 0);
});

document.addEventListener('keydown', event => {
    if (!localPreview || dialog.open || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey ||
        event.target.closest('#navigation, input, select, textarea, [contenteditable]:not([contenteditable="false"])')) return;
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    selectVariant(event.key === 'ArrowLeft' ? -1 : 1);
});

document.addEventListener('click', event => {
    const control = event.target.closest('button');
    if (!control) return;
    if (localPreview && control.dataset.variant) {
        selectVariant(Number(control.dataset.variant));
        return;
    }
    if (main.hasAttribute('aria-busy')) return;
    if ('category' in control.dataset) {
        state.category = control.dataset.category;
        main.querySelectorAll('[data-category]').forEach(chip => chip.setAttribute('aria-pressed', chip === control));
        renderResults();
        return;
    }
    const dish = state.dishes.find(entry => entry.id === Number(control.dataset.id));
    switch (control.dataset.action) {
        case 'random': {
            const candidates = filteredDishes().filter(entry => entry.available && !entry.avoided);
            if (!candidates.length) {
                openDialog('没有符合条件的推荐', '<p class="muted">试试清空关键词、提高预算或调整收藏筛选。</p>', control);
                break;
            }
            const selected = candidates[Math.floor(Math.random() * candidates.length)];
            showDish(selected, control, candidates.length);
            break;
        }
        case 'detail':
            showDish(dish, control);
            break;
        case 'favorite':
        case 'avoided':
            setMark(dish, control.dataset.action, control);
            break;
        case 'record':
            record(dish, control);
            break;
        case 'close':
            dialog.close();
            break;
        case 'browse-favorites':
            Object.assign(state, emptyFilters, { mark: 'favorite' });
            location.hash = 'catalog';
            break;
        case 'reset':
            Object.assign(state, emptyFilters);
            render();
            main.querySelector('[data-action="reset"]').focus();
            break;
    }
});

main.addEventListener('input', event => {
    if (!event.target.matches('[data-filter="keyword"]')) return;
    state.keyword = event.target.value;
    renderResults();
});

main.addEventListener('change', event => {
    const field = event.target;
    if (field.matches('select[data-filter]')) {
        state[field.dataset.filter] = field.value;
        renderResults();
    } else if (field.id === 'month') {
        state.month = field.value || campusDate().slice(0, 7);
        field.value = state.month;
        renderDiary();
    }
});

dialog.addEventListener('change', event => {
    if (Object.hasOwn(ratingFields, event.target.name)) dialog.querySelector('#form-error').textContent = '';
    if (event.target.name !== 'dishId') return;
    const dish = state.dishes.find(entry => entry.id === Number(event.target.value));
    event.target.form.elements.paidAmount.value = Number(dish.price);
});

dialog.addEventListener('submit', event => {
    if (event.target.id !== 'meal-form') return;
    event.preventDefault();
    const data = new FormData(event.target);
    const ratings = Object.keys(ratingFields).map(key => [key, Number(data.get(key))]);
    const rated = ratings.filter(([, value]) => value > 0).length;
    if (rated > 0 && rated !== ratings.length) {
        dialog.querySelector('#form-error').textContent = '请填写全部四项评分，或全部留空。';
        return;
    }
    const dish = state.dishes.find(entry => entry.id === Number(data.get('dishId')));
    const eatenAt = data.get('eatenAt');
    const totalAmount = Number(data.get('paidAmount'));
    state.drafts.push({
        eatenAt, totalAmount, mealType: data.get('mealType'), note: data.get('note').trim(), draft: true,
        items: [{ dishId: dish.id, dishName: dish.name, quantity: Number(data.get('quantity')), paidAmount: totalAmount,
            rating: rated ? Object.fromEntries(ratings) : null }],
    });
    state.month = eatenAt.slice(0, 7);
    dialog.close();
    if (view() === 'diary') render();
    else location.hash = 'diary';
    announce('已加入用餐记录 · 原型草稿');
});

dialog.addEventListener('close', () => {
    if (dialogTrigger?.isConnected) dialogTrigger.focus({ preventScroll: true });
    else main.focus({ preventScroll: true });
});

load();
