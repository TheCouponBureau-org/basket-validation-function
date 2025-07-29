function validate_basket_helper(basket_validation_input, discount_calculation = false) {
    
    const { basket, coupons } = basket_validation_input;
    if(!basket || !coupons) {
        return {
            discount_in_cents: 0,
            applied_coupons: []
        };
    }

    // Get individual coupons discount in cents
    if ( !discount_calculation ) {
        coupons.forEach(coupon => {
            let validateCouponWithBasket = validate_basket_helper({basket, coupons: [coupon]}, true);
            coupon.discount_in_cents = validateCouponWithBasket.basket_validation_output.discount_in_cents;
        });
    }

    // Sort the coupons with discount in cents in desc order
    coupons.sort((a, b) => b.discount_in_cents - a.discount_in_cents);

    const basket_validation_output = {
        discount_in_cents: 0,
        applied_coupons: []
    };

    let new_basket = mergeBasketItems(basket);
    let not_all_coupons_consumed = false;
    let index = 0;

    // for each coupon, check if basket meets requirements
    coupons.forEach(coupon => {
        index++;
        if(!coupon.purchase_requirement) {
            console.error("Coupon does not have purchase requirement");
            return;
        }
        let new_basket_total_price = 0;
        new_basket.map(item => {
            new_basket_total_price += item.price * item.quantity * 100;
        });

        // check if basket meets requirements
        let {status, basket_items, units_to_purchase, units_to_purchase2, units_to_purchase3} = meets_requirements(new_basket, coupon);
        if(status) {
            const has_only_primary_purchase = !units_to_purchase2 && !units_to_purchase3;
            let discount_in_cents = get_discount_in_cents(coupon, basket_items, has_only_primary_purchase, new_basket_total_price, []);
            //in case discount_in_cents < 0, coupon is not applicable
            if(discount_in_cents <= 0)
                return;
            const old_basket_units = basket_units(new_basket);
            const reduced_basket = reduce_basket(new_basket, basket_items, {units_to_purchase, units_to_purchase2, units_to_purchase3});
            const consumed_basket = reduced_basket.consumed_basket;
            
            discount_in_cents = get_discount_in_cents(coupon, basket_items, has_only_primary_purchase, new_basket_total_price, consumed_basket);
            new_basket = reduced_basket.new_basket;
            
            const new_basket_units = basket_units(new_basket);
            basket_validation_output.applied_coupons.push({
                coupon_code: coupon.gs1,
                // description: coupon.description,
                face_value_in_cents: discount_in_cents,
                product_codes: get_product_codes(consumed_basket),
            });

            if(discount_in_cents)
                basket_validation_output.discount_in_cents += discount_in_cents;

            if(new_basket_units === 0 && index < coupons.length) {
                not_all_coupons_consumed = true;
            }
        }
    });

    
    return {basket_validation_output};
}

function get_discount_in_cents(coupon, basket_items, has_only_primary_purchase, new_basket_total_price, consumed_basket) {
    const save_value_code = coupon.purchase_requirement.save_value_code || 0;
    let applies_to_which_item = coupon.purchase_requirement.applies_to_which_item;
    if(has_only_primary_purchase && !applies_to_which_item)
        applies_to_which_item = 0;
    let discount_in_cents = 0;
    if(save_value_code === 0) {
        discount_in_cents = coupon.purchase_requirement.primary_purchase_save_value;
        if(applies_to_which_item >= 0 || applies_to_which_item === undefined) {
            //coupon is not valid if total basket price is less than save value
            if(new_basket_total_price < discount_in_cents) {
                //discount_in_cents = qualifying_purchase_price;
                return -1;
            }
            let new_basket_items = applicable_basket_items(basket_items, applies_to_which_item);
            if(consumed_basket.length > 0) {
                new_basket_items = new_basket_items.filter((new_basket_item) => {
                    let found = false;
                    consumed_basket.map((consumed_basket_item) => {
                       if(consumed_basket_item.product_code === new_basket_item.product_code) {
                           found = true;
                       }
                    });
                    return found;
                    
                });
            }
            let qualifying_purchase_price = 0;
            new_basket_items.map(item => {
                qualifying_purchase_price += item.price * item.quantity * 100;
            });

            if(qualifying_purchase_price < discount_in_cents) {
                discount_in_cents = 0;
            }
            
            if(consumed_basket.length > 0) {
                let consumed_basket_price = 0;
                consumed_basket.map(item => {
                    consumed_basket_price += item.price * item.quantity * 100;
                });

                if(consumed_basket_price < discount_in_cents) {
                    discount_in_cents = 0;
                }
            }
        }
    } else if (save_value_code === 1) {
        const max_amount_to_purchase = coupon.purchase_requirement.primary_purchase_save_value;
        let new_basket_items = applicable_basket_items(basket_items, applies_to_which_item);
        discount_in_cents = new_basket_items[0].price * 100;
        if(max_amount_to_purchase !== 0
            && discount_in_cents > max_amount_to_purchase) {
            discount_in_cents = max_amount_to_purchase;
        }
    } else if (save_value_code === 2) {
        const free_purchase_item_units = coupon.purchase_requirement.primary_purchase_save_value;
        let index = 0;
        basket_items.map(item => {
            if(applies_to_which_item === 0 && item.purchase_type) {
                return;
            }
            if(applies_to_which_item === 1 && item.purchase_type !== "second_purchase") {
                return;
            }
            if(applies_to_which_item === 2 && item.purchase_type !== "third_purchase") {
                return;
            }
            for(let i = 0; i < item.quantity; i++) {
                if(index < free_purchase_item_units) {
                    discount_in_cents += item.price * 100;
                    index++;
                }
            }
        });
    } else if(save_value_code === 6) {
        discount_in_cents = coupon.purchase_requirement.primary_purchase_save_value;
    }
    return discount_in_cents;
}

function applicable_basket_items(basket_items, applies_to_which_item) {
    let new_basket_items = [];
    if(applies_to_which_item === undefined) {
        new_basket_items = basket_items;
    } else if (applies_to_which_item === 0) {
        new_basket_items = basket_items.filter(item => !item.purchase_type);
    } else if(applies_to_which_item === 1) {
        new_basket_items = basket_items.filter(item => item.purchase_type === "second_purchase");
    } else if(applies_to_which_item === 2) {
        new_basket_items = basket_items.filter(item => item.purchase_type === "third_purchase");
    }
    return new_basket_items;
}

function basket_units(basket) {
    return basket.map(item => item.quantity).reduce((a, b) => a + b, 0);
}

function reduce_basket(basket, allowed_basket_items, obj_units_to_purchase) {
    // let {units_to_purchase, units_to_purchase2, units_to_purchase3} = obj_units_to_purchase;
    const new_basket = [];
    const consumed_basket = [];
    basket.map(item => {
        let basket_item = {...item};
        let consumed_basket_item = {};
        let allowed_basket_item;
        for(const key_units_to_purchase of ["units_to_purchase", "units_to_purchase2", "units_to_purchase3"]) {
            const units_to_purchase = obj_units_to_purchase[key_units_to_purchase];
            if(units_to_purchase > 0
                && basket_item.quantity > 0) {
                allowed_basket_item = allowed_basket_items_includes(allowed_basket_items, basket_item, key_units_to_purchase);
                if(allowed_basket_item) {
                    if(basket_item.quantity > units_to_purchase) {
                        consumed_basket_item = {
                            ...basket_item,
                            quantity: units_to_purchase
                        };
                        basket_item = {
                            ...basket_item,
                            quantity: basket_item.quantity - units_to_purchase
                        };
                        obj_units_to_purchase[key_units_to_purchase] = 0;
                    } else {
                        consumed_basket_item = {...basket_item};
                        obj_units_to_purchase[key_units_to_purchase] -= basket_item.quantity;
                        basket_item = {
                            ...basket_item,
                            quantity: 0
                        };
                    }
                    //from reusable purchase, reduce obj_units_to_purchase[key_units_to_purchase] by consumed_basket_item.quantity
                    if(allowed_basket_item.purchase_reuse) {
                        for(const key_units_to_purchase_other of ["units_to_purchase", "units_to_purchase2", "units_to_purchase3"]) {
                            if(key_units_to_purchase_other !== key_units_to_purchase && obj_units_to_purchase[key_units_to_purchase_other]) {
                                //find quantity of key_units_to_purchase_other already added
                                const allowed_basket_item_other = allowed_basket_items_includes(allowed_basket_items, consumed_basket_item, key_units_to_purchase_other);
                                if(allowed_basket_item_other?.quantity > 0) {
                                    let reduce_quantity_other = (obj_units_to_purchase[key_units_to_purchase_other] <= consumed_basket_item.quantity) ? obj_units_to_purchase[key_units_to_purchase_other] :  consumed_basket_item.quantity;
                                    reduce_quantity_other = (obj_units_to_purchase[key_units_to_purchase_other] <= reduce_quantity_other) ? obj_units_to_purchase[key_units_to_purchase_other] :  reduce_quantity_other;
                                    obj_units_to_purchase[key_units_to_purchase_other] -= reduce_quantity_other;
                                }
                            }
                        }
                    }
                }
            }
        }
        if(consumed_basket_item.quantity > 0)
            consumed_basket.push(consumed_basket_item);
        if(basket_item.quantity > 0)
            new_basket.push(basket_item);
    });
    return {consumed_basket, new_basket};
}

function allowed_basket_items_includes(allowed_basket_items, item, key_units_to_purchase) {
    const allowed_basket_item = allowed_basket_items.find(allowed_basket_item => {
        if(key_units_to_purchase === "units_to_purchase" && allowed_basket_item.purchase_type) {
            return false;
        }
        if(key_units_to_purchase === "units_to_purchase2" && allowed_basket_item.purchase_type !== "second_purchase") {
            return false;
        }
        if(key_units_to_purchase === "units_to_purchase3" && allowed_basket_item.purchase_type !== "third_purchase") {
            return false;
        }
        return allowed_basket_item.product_code === item.product_code;
        //review if need to match price and unit as well
    });
    return allowed_basket_item;
}

function get_product_codes(basket_items) {
    const product_codes = {};
    basket_items.map(item => {
        if(!item.product_type) {
            product_codes.gtins = product_codes.gtins || [];
            product_codes.gtins.push(item.product_code);
        } else {
            product_codes[item.product_type] = product_codes[item.product_type] || [];
            product_codes[item.product_type].push(item.product_code);
        }
        return item.product_code;
    });
    return product_codes;
}

function meets_requirements(basket, coupon) {
    if(!coupon.purchase_requirement) {
        console.error("Coupon does not have purchase requirement");
        return NEGATIVE_STATUS;
    }
    const { save_value_code: save_value_code1, applies_to_which_item, additional_purchase_rules_code} = coupon.purchase_requirement;
    const {primary_purchase, second_purchase, third_purchase} = get_purchases(coupon.purchase_requirement);
    // const save_value_code = save_value_code1 || 0;

    if(additional_purchase_rules_code === undefined || additional_purchase_rules_code === null) {
        let {status, basket_items, units_to_purchase} =  meets_purchase_requirements(coupon, basket, primary_purchase, true);
        // units_to_purchase += get_additional_units_to_purchase(basket_items, units_to_purchase, primary_purchase);
        return {status, basket_items, units_to_purchase};
    }
    else if (additional_purchase_rules_code === 0) {
        const purchases = [primary_purchase, second_purchase, third_purchase];
        const purchase_types = ["", "second_purchase", "third_purchase"];

        if (applies_to_which_item === undefined) {
            for (let basket_item of basket) {
                for (let i = 0; i < purchases.length; i++) {
                    const purchase = purchases[i];
                    if (purchase?.req_code !== undefined && purchase?.requirements !== undefined) {
                        let { status, basket_items, units_to_purchase } = meets_purchase_requirements(coupon, basket, purchase, true);
                        // Check if the current basket item is part of the basket_items that satisfy the condition
                        if (status && basket_items.some(item => item.product_code === basket_item.product_code)) {
                            // Calculate the total sum
                            const totalValue = basketValue(basket_items);
                            if(primary_purchase.save_value < totalValue) {
                                if (i > 0) {
                                    basket_items = basket_items?.map(item => ({
                                        ...item,
                                        purchase_type: purchase_types[i]
                                    }));
                                }
                                return {
                                    status,
                                    basket_items,
                                    ...(i === 0 && { units_to_purchase }),
                                    ...(i === 1 && { units_to_purchase2: units_to_purchase }),
                                    ...(i === 2 && { units_to_purchase3: units_to_purchase })
                                };
                            }  
                        }
                    }
                }
            }
        } else if (applies_to_which_item >= 0 && applies_to_which_item <= 2) {
            const purchase = purchases[applies_to_which_item];
            if (purchase?.req_code !== undefined && purchase?.requirements !== undefined) {
                let { status, basket_items, units_to_purchase } = meets_purchase_requirements(coupon, basket, purchase, true);
                if (status) {
                    if (applies_to_which_item > 0) {
                        basket_items = basket_items?.map(item => ({ ...item, purchase_type: purchase_types[applies_to_which_item] }));
                    }
                    // Dynamically assign correct units_to_purchase based on applies_to_which_item
                    return {
                        status,
                        basket_items,
                        ...(applies_to_which_item === 0 && { units_to_purchase }),
                        ...(applies_to_which_item === 1 && { units_to_purchase2: units_to_purchase }),
                        ...(applies_to_which_item === 2 && { units_to_purchase3: units_to_purchase })
                    };
                }
            }
        }
    } 
    else if (additional_purchase_rules_code === 1) {
        let basket_items1, units_to_purchase1;
        let {status, basket_items, units_to_purchase} = meets_purchase_requirements(coupon, basket, primary_purchase, false);
        if(!status) {
            return NEGATIVE_STATUS;
        }
        basket_items1 = basket_items;
        units_to_purchase1 = units_to_purchase;

        let basket_items2, units_to_purchase2;
        if(second_purchase.req_code !== undefined && second_purchase.requirements !== undefined) {
            let {status, basket_items, units_to_purchase} = meets_purchase_requirements(coupon, basket, second_purchase, false);
            if(!status) {
                return NEGATIVE_STATUS;
            }
            basket_items2 = basket_items;
            units_to_purchase2 = units_to_purchase;
        }

        let basket_items3, units_to_purchase3;
        if(third_purchase.req_code !== undefined && third_purchase.requirements !== undefined) {
            let {status, basket_items, units_to_purchase} = meets_purchase_requirements(coupon, basket, third_purchase, false);
            if(!status) {
                return NEGATIVE_STATUS;
            }
            basket_items3 = basket_items;
            units_to_purchase3 = units_to_purchase;
        }
        basket_items2 = basket_items2?.map(item => {
            return {
                ...item,
                purchase_type: "second_purchase"
            };
        });
        basket_items3 = basket_items3?.map(item => {
            return {
                ...item,
                purchase_type: "third_purchase"
            };
        });
        const basket_items_final = reorderSubBasket(basket, basket_items1.concat(basket_items2 || []).concat(basket_items3 || []));
        // units_to_purchase1 += get_additional_units_to_purchase(basket_items_final, units_to_purchase1, primary_purchase);
        // units_to_purchase2 += get_additional_units_to_purchase(basket_items_final, units_to_purchase2, second_purchase);
        // units_to_purchase3 += get_additional_units_to_purchase(basket_items_final, units_to_purchase3, third_purchase);
        return {status: true,
            basket_items: basket_items_final,
            units_to_purchase: units_to_purchase1,
            units_to_purchase2: units_to_purchase2,
            units_to_purchase3: units_to_purchase3
        };
    } else if (additional_purchase_rules_code === 2) {
        let basket_items1, units_to_purchase1;
        let {status, basket_items, units_to_purchase} = meets_purchase_requirements(coupon, basket, primary_purchase, false);
        if(!status) {
            return NEGATIVE_STATUS;
        }
        basket_items1 = basket_items;
        units_to_purchase1 = units_to_purchase;

        let status2, basket_items2, units_to_purchase2;
        if(second_purchase.req_code !== undefined && second_purchase.requirements !== undefined) {
            let {status, basket_items, units_to_purchase} = meets_purchase_requirements(coupon, basket, second_purchase, false);
            status2 = status;
            basket_items2 = basket_items;
            units_to_purchase2 = units_to_purchase;
        }
        let status3, basket_items3, units_to_purchase3;
        if(third_purchase.req_code !== undefined && third_purchase.requirements !== undefined) {
            let {status, basket_items, units_to_purchase} = meets_purchase_requirements(coupon, basket, third_purchase, false);
            status3 = status;
            basket_items3 = basket_items;
            units_to_purchase3 = units_to_purchase;
        }
        if(status2) {
            basket_items2 = basket_items2?.map(item => {
                return {
                    ...item,
                    purchase_type: "second_purchase"
                };
            });
        } else if(status3) {
            basket_items3 = basket_items3?.map(item => {
                return {
                    ...item,
                    purchase_type: "third_purchase"
                };
            });
        } else {
            return NEGATIVE_STATUS;
        }

        const basket_items_final = reorderSubBasket(basket, basket_items1.concat(
            status2 ? basket_items2 : status3 ? basket_items3 : []
        ));
        // units_to_purchase1 += get_additional_units_to_purchase(basket_items_final, units_to_purchase1, primary_purchase);
        // if(status2)
        //     units_to_purchase2 += get_additional_units_to_purchase(basket_items_final, units_to_purchase2, second_purchase);
        // else if(status3)
        //     units_to_purchase3 += get_additional_units_to_purchase(basket_items_final, units_to_purchase3, third_purchase);
        return {status: true,
            basket_items: basket_items_final,
            units_to_purchase: units_to_purchase1,
            units_to_purchase2: units_to_purchase2,
            units_to_purchase3: units_to_purchase3
        };
    }
    return NEGATIVE_STATUS;
}

function get_additional_units_to_purchase(coupon, basket_items, units_to_purchase, purchase) {
    if(purchase.req_code === 0) {
        let total_price_units_to_purchase = 0;
        let count = 0;
        let additional_units_to_purchase = 0;
        basket_items.map(item => {
            for(let i = 0; i < item.quantity; i++) {
                if(count < units_to_purchase) {
                    total_price_units_to_purchase += (item.price * 100);
                } else {
                    if(coupon.purchase_requirement.primary_purchase_save_value > total_price_units_to_purchase) {
                        additional_units_to_purchase++;
                        total_price_units_to_purchase += (item.price * 100);
                    }
                }
                count++;
            }
        });
        return additional_units_to_purchase;
    }
    return 0;
}

function meets_purchase_requirements(coupon, basket, purchase, apply_additional_units) {
    let units_to_purchase = 0;
    if(purchase.req_code === 0) {
        units_to_purchase = purchase.requirements;
        const {status, basket_items} = basket_has_units_to_purchase(basket, units_to_purchase, purchase);
        if(apply_additional_units)
            units_to_purchase += get_additional_units_to_purchase(coupon, basket_items, units_to_purchase, purchase);
        return {status, basket_items, units_to_purchase};
    } else if(purchase.req_code === 1) {
        let cash_value_total_transaction = 0;
        let units_to_purchase = 0;
        const new_basket = allowed_basket(basket, purchase);
        new_basket.map(item => {
            for(let i = 0; i < item.quantity; i++) {
                if(cash_value_total_transaction < purchase.requirements) {
                    cash_value_total_transaction += (item.price * 100);
                    units_to_purchase++;
                }
            }
        });
        const status = cash_value_total_transaction >= purchase.requirements;
        return {status, basket_items: new_basket, units_to_purchase};
    } else if(purchase.req_code === 2) {
        let cash_value_total_transaction = 0;
        let units_to_purchase = 0;
        basket.map(item => {
            for(let i = 0; i < item.quantity; i++) {
                if(cash_value_total_transaction < purchase.requirements) {
                    cash_value_total_transaction += (item.price * 100);
                    units_to_purchase++;
                }
            }
        });
        basket = basket?.map(item => {
            return {
                ...item,
                purchase_reuse: true
            };
        });
        const status = cash_value_total_transaction >= purchase.requirements;
        return {status, basket_items: basket, units_to_purchase};
    }
    return NEGATIVE_STATUS;
}

function basket_has_units_to_purchase(basket, units_to_purchase, {
    gtins, excluded_gtins,
    eans, excluded_eans,
    prefixed_code, excluded_prefixed_code
}) {
    // if(basket.length < units_to_purchase)
    //     return NEGATIVE_STATUS;

    const allowed_basket_items = allowed_basket(basket, {
        gtins, excluded_gtins,
        eans, excluded_eans,
        prefixed_code, excluded_prefixed_code
    });
    const units_purchased = allowed_basket_items.map(item => item.quantity).reduce((a, b) => a + b, 0);

    return {
        status: units_purchased >= units_to_purchase,
        basket_items: allowed_basket_items,
    };
}

function allowed_basket(basket, {
    gtins, excluded_gtins,
    eans, excluded_eans,
    prefixed_code, excluded_prefixed_code
}) {
    const allowed_basket_items = basket.filter(item => {
        if ( !prefixed_code ) {
            return true;
        }
        const range = prefixed_code[item.product_type];
        const excluded_range = excluded_prefixed_code[item.product_type];
        if(excluded_range
            && item.product_code >= excluded_range.start
            && item.product_code <= excluded_range.end) {
            return false;
        }
        if(excluded_gtins?.includes(item.product_code)
            || excluded_eans?.includes(item.product_code)) {
            return false;
        }
        if(!gtins?.includes(item.product_code)
            && !eans?.includes(item.product_code)
            && !range) {
            return false;
        }
        if(range
            && (item.product_code < range.start
                || item.product_code > range.end)) {
            return false;
        }
        return true;
    });
    return allowed_basket_items;
}

// [
//   "PLU:mobispark.thecouponbureau.org:1001_1100",
//   "CLS:mobispark.thecouponbureau.org:1001_1100",
//   "DPT:mobispark.thecouponbureau.org:1001_1100",
//   "C_D:mobispark.thecouponbureau.org:1001_1100"
// ]
// transforms to
// {
//   "PLU": ["1001", "1100"],
//   "CLS": ["1001", "1100"],
//   "DPT": ["1001", "1100"],
//   "C_D": ["1001", "1100"],
// }
// function transform_prefixed_code(prefixed_code) {
//     if ( !Array.isArray(prefixed_code) ) {
//         return prefixed_code;
//     }
//     const output = {};
//     prefixed_code.map(code => {
//         if(code) {
//             const parts = code.split(":");
//             const range = parts[2].split("_");
//             output[parts[0]] = {
//                 start: range[0],
//                 end: range[1],
//             };
//         }
//     });
//     return output;
// }


function transformString(input) {
    let parts = input.split(":"); // Split by ":"
    let key = parts[0]; // Extract X
    let values = parts[2].split("-").map(String); // Split Z values by "-" and convert to numbers

    return { [key]: values };
}

function transform_prefixed_code(prefixed_code) {
    if ( !prefixed_code ) {
        return [];
    }
    if ( Array.isArray(prefixed_code) ) {
        for ( let i=0; i<prefixed_code.length; i++ ) {
            let code = prefixed_code[i];
            // if code is object then break
            if ( typeof code === 'object' ) {
                return;
            }
            if ( code.indexOf(":") >= 0 ) {
                // In this format "PLU:mobispark.thecouponbureau.org:2001_2100" - convert to {PLU: [2001, 2100]}
                code = transformString(code);
                prefixed_code[i] = code;
            }
        }
        
        return prefixed_code;
    }
    const output = {};
    for (const property in prefixed_code) {
        if ( property.indexOf(":") >= 0 ) {
            // In this format "PLU:mobispark.thecouponbureau.org:2001_2100" - convert to {PLU: [2001, 2100]}
            property = transformString(property);
        }
        const code = prefixed_code[property];
        if(code) {
            const range = code[0].split("_");
            output[property] = {
                start: range[0],
                end: range[1],
            };
        }
    }
    
    return output;
}

const NEGATIVE_STATUS = {
    status: false,
};

const POSITIVE_STATUS = {
    status: true,
};

function get_purchases(purchase_requirement) {
    return {
        primary_purchase: {
            save_value: purchase_requirement.primary_purchase_save_value,
            requirements: purchase_requirement.primary_purchase_requirements,
            req_code: purchase_requirement.primary_purchase_req_code,
            gtins: purchase_requirement.primary_purchase_gtins,
            eans: purchase_requirement.primary_purchase_eans,
            excluded_gtins: purchase_requirement.excluded_primary_purchase_gtins,
            excluded_eans: purchase_requirement.excluded_primary_purchase_eans,
            prefixed_code: transform_prefixed_code(purchase_requirement.primary_purchase_prefixed_code),
            excluded_prefixed_code: transform_prefixed_code(purchase_requirement.excluded_primary_purchase_prefixed_code),
        },
        second_purchase: {
            save_value: purchase_requirement.second_purchase_save_value,
            requirements: purchase_requirement.second_purchase_requirements,
            req_code: purchase_requirement.second_purchase_req_code,
            gtins: purchase_requirement.second_purchase_gtins,
            eans: purchase_requirement.second_purchase_eans,
            excluded_gtins: purchase_requirement.excluded_second_purchase_gtins,
            excluded_eans: purchase_requirement.excluded_second_purchase_eans,
            prefixed_code: transform_prefixed_code(purchase_requirement.second_purchase_prefixed_code),
            excluded_prefixed_code: transform_prefixed_code(purchase_requirement.excluded_second_purchase_prefixed_code),
        },
        third_purchase: {
            save_value: purchase_requirement.third_purchase_save_value,
            requirements: purchase_requirement.third_purchase_requirements,
            req_code: purchase_requirement.third_purchase_req_code,
            gtins: purchase_requirement.third_purchase_gtins,
            eans: purchase_requirement.third_purchase_eans,
            excluded_gtins: purchase_requirement.excluded_third_purchase_gtins,
            excluded_eans: purchase_requirement.excluded_third_purchase_eans,
            prefixed_code: transform_prefixed_code(purchase_requirement.third_purchase_prefixed_code),
            excluded_prefixed_code: transform_prefixed_code(purchase_requirement.excluded_third_purchase_prefixed_code),
        },
    }
}

// Function to merge same product codes
function mergeBasketItems(basket) {
    let mergedBasket = {};

    basket.forEach(item => {
        let key = `${item.product_code}-${item.price.toFixed(2)}`; // Unique key with formatted price
        if (mergedBasket[key]) {
            mergedBasket[key].quantity += item.quantity; // Merge quantity
        } else {
            mergedBasket[key] = { 
                product_code: item.product_code, 
                price: parseFloat(item.price.toFixed(2)), // Keep price in decimal format
                quantity: item.quantity, 
                unit: item.unit 
            };
        }
    });

    return Object.values(mergedBasket); // Convert merged object back to array
}

function reorderSubBasket(mainBasket, subBasket) {
    // Create a map of product_code indexes from the main basket
    const orderMap = new Map();
    mainBasket.forEach((item, index) => {
        orderMap.set(item.product_code, index);
    });

    // Sort the sub basket based on the main basket order
    subBasket.sort((a, b) => {
        return (orderMap.get(a.product_code) ?? Infinity) - (orderMap.get(b.product_code) ?? Infinity);
    });

    return subBasket;
}

function basketValue(basket) {
    const totalValue = basket.reduce((sum, item) => {
        return sum + item.price * item.quantity * 100;
    }, 0);
    return totalValue;
}

    
module.exports = {
    validate_basket_helper
}
