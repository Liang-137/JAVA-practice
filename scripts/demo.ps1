param([string]$BaseUrl = 'http://127.0.0.1:8080', [string]$PhotoPath = '')
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
if ($PhotoPath -and -not (Test-Path -LiteralPath $PhotoPath -PathType Leaf)) { throw 'PhotoPath does not exist.' }

function Invoke-FoodApi {
    param([string]$Method, [string]$Path, $Body = $null)
    $request = @{ Method=$Method; Uri="$BaseUrl$Path" }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json; charset=utf-8'
        $request.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10))
    }
    Invoke-RestMethod @request
}

Write-Host '1. 检查服务'
Invoke-FoodApi 'GET' '/api/health' | Format-List
$suffix = [guid]::NewGuid().ToString('N').Substring(0,6)
Write-Host '2. 新建食堂、档口和菜品（每次演示使用独立名称）'
$canteen = Invoke-FoodApi 'POST' '/api/canteens' @{name="演示食堂-$suffix";location='校园东侧'}
$stall = Invoke-FoodApi 'POST' '/api/stalls' @{canteenId=$canteen.id;name='家常菜档口'}
$rice = Invoke-FoodApi 'POST' '/api/dishes' @{stallId=$stall.id;name='番茄牛腩饭';price=15;category='米饭';flavors=@('不辣','酸甜')}
$soup = Invoke-FoodApi 'POST' '/api/dishes' @{stallId=$stall.id;name='冬瓜汤';price=4;category='汤';flavors=@('清淡','不辣')}
$rice | Format-List

Write-Host '3. 收藏菜品，记录一餐两个菜并评分'
Invoke-FoodApi 'PUT' "/api/dishes/$($rice.id)/marks" @{favorite=$true;avoided=$false} | Out-Null
$meal = Invoke-FoodApi 'POST' '/api/meals' @{
    eatenAt=(Get-Date).AddMinutes(-1).ToString('yyyy-MM-ddTHH:mm:ss');mealType='LUNCH';note='中期演示：一餐两个菜'
    items=@(
        @{dishId=$rice.id;quantity=1;paidAmount=14.50;rating=@{taste=5;valueScore=4;portion=5;recommendation=5};comment='牛肉很软'}
        @{dishId=$soup.id;quantity=1;paidAmount=3.50;rating=$null;comment='清淡解腻'}
    )
}
if ($meal.totalAmount -ne 18) { throw 'Unexpected meal total.' }
$meal | ConvertTo-Json -Depth 8

Write-Host '4. 按预算和口味筛选、随机推荐'
$flavor = [Uri]::EscapeDataString('不辣')
Invoke-FoodApi 'GET' "/api/dishes?canteenId=$($canteen.id)&maxPrice=15&flavor=$flavor" | ConvertTo-Json -Depth 6
Invoke-FoodApi 'GET' "/api/recommendations/random?canteenId=$($canteen.id)&maxPrice=15" | ConvertTo-Json -Depth 6

Write-Host '5. 查看个人榜单及本月消费'
Invoke-FoodApi 'GET' '/api/rankings?metric=OVERALL' | ConvertTo-Json -Depth 5
$month = (Get-Date).ToString('yyyy-MM')
Invoke-FoodApi 'GET' "/api/statistics/monthly?month=$month" | Select-Object month,totalAmount,mealCount,averagePerMeal | Format-List

Write-Host '6. 将汤标记为踩雷，验证推荐会排除它'
Invoke-FoodApi 'PUT' "/api/dishes/$($soup.id)/marks" @{favorite=$false;avoided=$true} | Out-Null
$result = Invoke-FoodApi 'GET' "/api/recommendations/random?canteenId=$($canteen.id)"
if ($result.dish.id -ne $rice.id) { throw 'Avoided dish was unexpectedly recommended.' }
$result | ConvertTo-Json -Depth 5

if ($PhotoPath) {
    Write-Host '7. 上传真实菜品照片'
    & curl.exe --fail-with-body --silent --show-error -F "file=@$PhotoPath" "$BaseUrl/api/dishes/$($rice.id)/photos"
    if ($LASTEXITCODE -ne 0) { throw 'Photo upload failed.' }
}
Write-Host "演示完成。记录已保存在数据库中；用餐 ID: $($meal.id)"
