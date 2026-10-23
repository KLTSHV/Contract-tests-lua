-- Второе расширение показывает, что Java-движок не привязан к пользователям.
function validate(order, response)
    if response.status ~= 200 then return false, "Ожидался HTTP 200" end
    if type(order) ~= "table" or type(order.items) ~= "table" or #order.items == 0 then
        return false, "items должен содержать позиции заказа"
    end
    local total = 0
    for index, item in ipairs(order.items) do
        if type(item) ~= "table" or type(item.price) ~= "number" or item.price < 0 then
            return false, "Некорректная цена в позиции " .. index
        end
        if type(item.quantity) ~= "number" or item.quantity <= 0 or item.quantity % 1 ~= 0 then
            return false, "Некорректное количество в позиции " .. index
        end
        total = total + item.price * item.quantity
    end
    if type(order.total) ~= "number" or math.abs(order.total - total) > 0.001 then
        return false, "total не совпадает с суммой позиций: " .. total
    end
    return true, "Контракт заказа выполнен"
end
